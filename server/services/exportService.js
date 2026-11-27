// Генератор финансовых отчётов FinKeeper24 в форматах Excel (.xlsx, OpenXML ECMA-376) и CSV (UTF-8 BOM).
// Не требует внешних npm-зависимостей (использует встроенный zlib для сборки ZIP-контейнера .xlsx).
const zlib = require('zlib');

const MONTH_NAMES_RU = [
    '',
    'Январь', 'Февраль', 'Март', 'Апрель', 'Май', 'Июнь',
    'Июль', 'Август', 'Сентябрь', 'Октябрь', 'Ноябрь', 'Декабрь',
];

function formatMonthLabel(year, month) {
    const name = MONTH_NAMES_RU[month] || String(month);
    return `${name} ${year}`;
}

function escapeXml(value) {
    return String(value ?? '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&apos;');
}

function columnNumberToName(colIndex1Based) {
    let n = colIndex1Based;
    let name = '';
    while (n > 0) {
        const rem = (n - 1) % 26;
        name = String.fromCharCode(65 + rem) + name;
        n = Math.floor((n - 1) / 26);
    }
    return name;
}

// --- CRC32 для ZIP-архива ---
const CRC_TABLE = (() => {
    const table = new Uint32Array(256);
    for (let i = 0; i < 256; i++) {
        let c = i;
        for (let k = 0; k < 8; k++) {
            c = (c & 1) ? (0xedb88320 ^ (c >>> 1)) : (c >>> 1);
        }
        table[i] = c >>> 0;
    }
    return table;
})();

function crc32(buffer) {
    let crc = 0xffffffff;
    for (let i = 0; i < buffer.length; i++) {
        crc = CRC_TABLE[(crc ^ buffer[i]) & 0xff] ^ (crc >>> 8);
    }
    return (crc ^ 0xffffffff) >>> 0;
}

function buildZipBuffer(entries) {
    const localParts = [];
    const centralParts = [];
    let offset = 0;

    for (const entry of entries) {
        const nameBuf = Buffer.from(entry.name, 'utf8');
        const rawBuf = Buffer.isBuffer(entry.data) ? entry.data : Buffer.from(String(entry.data), 'utf8');
        const compressedBuf = zlib.deflateRawSync(rawBuf);
        const checksum = crc32(rawBuf);

        const localHeader = Buffer.alloc(30);
        localHeader.writeUInt32LE(0x04034b50, 0); // Local file header signature
        localHeader.writeUInt16LE(20, 4);         // Version needed to extract (2.0)
        localHeader.writeUInt16LE(0x0800, 6);     // General purpose bit flag (UTF-8)
        localHeader.writeUInt16LE(8, 8);          // Compression method: DEFLATE (8)
        localHeader.writeUInt16LE(0, 10);         // Mod time
        localHeader.writeUInt16LE(0x597a, 12);    // Mod date
        localHeader.writeUInt32LE(checksum, 14);
        localHeader.writeUInt32LE(compressedBuf.length, 18);
        localHeader.writeUInt32LE(rawBuf.length, 22);
        localHeader.writeUInt16LE(nameBuf.length, 26);
        localHeader.writeUInt16LE(0, 28);

        localParts.push(localHeader, nameBuf, compressedBuf);

        const centralHeader = Buffer.alloc(46);
        centralHeader.writeUInt32LE(0x02014b50, 0); // Central directory signature
        centralHeader.writeUInt16LE(20, 4);         // Version made by
        centralHeader.writeUInt16LE(20, 6);         // Version needed
        centralHeader.writeUInt16LE(0x0800, 8);     // UTF-8 flag
        centralHeader.writeUInt16LE(8, 10);         // DEFLATE
        centralHeader.writeUInt16LE(0, 12);
        centralHeader.writeUInt16LE(0x597a, 14);
        centralHeader.writeUInt32LE(checksum, 16);
        centralHeader.writeUInt32LE(compressedBuf.length, 20);
        centralHeader.writeUInt32LE(rawBuf.length, 24);
        centralHeader.writeUInt16LE(nameBuf.length, 28);
        centralHeader.writeUInt16LE(0, 30);
        centralHeader.writeUInt16LE(0, 32);
        centralHeader.writeUInt16LE(0, 34);
        centralHeader.writeUInt16LE(0, 36);
        centralHeader.writeUInt32LE(0, 38);
        centralHeader.writeUInt32LE(offset, 42);

        centralParts.push(centralHeader, nameBuf);
        offset += localHeader.length + nameBuf.length + compressedBuf.length;
    }

    const centralDirSize = centralParts.reduce((acc, buf) => acc + buf.length, 0);
    const eocd = Buffer.alloc(22);
    eocd.writeUInt32LE(0x06054b50, 0);
    eocd.writeUInt16LE(0, 4);
    eocd.writeUInt16LE(0, 6);
    eocd.writeUInt16LE(entries.length, 8);
    eocd.writeUInt16LE(entries.length, 10);
    eocd.writeUInt32LE(centralDirSize, 12);
    eocd.writeUInt32LE(offset, 16);
    eocd.writeUInt16LE(0, 20);

    return Buffer.concat([...localParts, ...centralParts, eocd]);
}

function buildWorksheetXml(rows, columnWidths = []) {
    const colsXml = columnWidths.length > 0
        ? `<cols>${columnWidths.map((w, idx) => `<col min="${idx + 1}" max="${idx + 1}" width="${w}" customWidth="1"/>`).join('')}</cols>`
        : '';

    const rowsXml = rows.map((row, rowIndex) => {
        const r = rowIndex + 1;
        const isHeader = rowIndex === 0;
        const cellsXml = row.map((cell, colIndex) => {
            const cellRef = `${columnNumberToName(colIndex + 1)}${r}`;
            const styleAttr = isHeader ? ' s="1"' : '';
            if (typeof cell === 'number' && Number.isFinite(cell)) {
                return `<c r="${cellRef}" t="n"${styleAttr}><v>${cell}</v></c>`;
            }
            return `<c r="${cellRef}" t="inlineStr"${styleAttr}><is><t>${escapeXml(cell ?? '')}</t></is></c>`;
        }).join('');
        return `<row r="${r}">${cellsXml}</row>`;
    }).join('');

    return `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
${colsXml}
<sheetData>${rowsXml}</sheetData>
</worksheet>`;
}

function generateXlsxBuffer(sheets) {
    const contentTypesXml = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
  ${sheets.map((_, idx) => `<Override PartName="/xl/worksheets/sheet${idx + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>`).join('\n  ')}
</Types>`;

    const rootRelsXml = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>`;

    const workbookXml = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    ${sheets.map((sheet, idx) => `<sheet name="${escapeXml(sheet.name)}" sheetId="${idx + 1}" r:id="rId${idx + 1}"/>`).join('\n    ')}
  </sheets>
</workbook>`;

    const workbookRelsXml = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  ${sheets.map((_, idx) => `<Relationship Id="rId${idx + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${idx + 1}.xml"/>`).join('\n  ')}
  <Relationship Id="rId${sheets.length + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>`;

    const stylesXml = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="2">
    <font><sz val="11"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
  </fonts>
  <fills count="3">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF10B981"/><bgColor indexed="64"/></patternFill></fill>
  </fills>
  <borders count="1">
    <border><left/><right/><top/><bottom/><diagonal/></border>
  </borders>
  <cellStyleXfs count="1">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
  </cellStyleXfs>
  <cellXfs count="2">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
  </cellXfs>
</styleSheet>`;

    const entries = [
        { name: '[Content_Types].xml', data: contentTypesXml },
        { name: '_rels/.rels', data: rootRelsXml },
        { name: 'xl/workbook.xml', data: workbookXml },
        { name: 'xl/_rels/workbook.xml.rels', data: workbookRelsXml },
        { name: 'xl/styles.xml', data: stylesXml },
        ...sheets.map((sheet, idx) => ({
            name: `xl/worksheets/sheet${idx + 1}.xml`,
            data: buildWorksheetXml(sheet.rows, sheet.columnWidths),
        })),
    ];

    return buildZipBuffer(entries);
}

function escapeCsvCell(value) {
    const str = String(value ?? '');
    if (str.includes(';') || str.includes('"') || str.includes('\n') || str.includes('\r')) {
        return `"${str.replace(/"/g, '""')}"`;
    }
    return str;
}

function generateCsvBuffer({ summaryRows, expenseRows, incomeRows, savingsRows }) {
    const lines = [];
    lines.push('СВОДКА ПО МЕСЯЦАМ');
    for (const row of summaryRows) {
        lines.push(row.map(escapeCsvCell).join(';'));
    }
    lines.push('');
    lines.push('РАСХОДЫ');
    for (const row of expenseRows) {
        lines.push(row.map(escapeCsvCell).join(';'));
    }
    lines.push('');
    lines.push('ДОХОДЫ');
    for (const row of incomeRows) {
        lines.push(row.map(escapeCsvCell).join(';'));
    }
    lines.push('');
    lines.push('КОПИЛКИ');
    for (const row of savingsRows) {
        lines.push(row.map(escapeCsvCell).join(';'));
    }

    return Buffer.from('\uFEFF' + lines.join('\r\n') + '\r\n', 'utf8');
}

function buildReportTables({ months, monthTotalsMap, expenses, incomes, savingsGoals }) {
    const summaryRows = [
        ['Месяц', 'Доходы (₽)', 'Расходы (₽)', 'В копилки (₽)', 'Остаток месяца (₽)'],
        ...months.map((m) => {
            const totals = monthTotalsMap.get(m.id) || { income: 0, visibleExpenses: 0, savings: 0, balance: 0 };
            return [
                formatMonthLabel(m.year, m.month),
                Math.round(totals.income),
                Math.round(totals.visibleExpenses),
                Math.round(totals.savings),
                Math.round(totals.balance),
            ];
        }),
    ];

    const expenseRows = [
        ['Дата', 'Месяц', 'Категория', 'Сумма (₽)', 'Комментарий'],
        ...expenses.map((e) => [
            e.date,
            formatMonthLabel(e.year, e.month),
            e.category_name,
            Math.round(Number(e.amount) || 0),
            e.comment || '',
        ]),
    ];

    const incomeRows = [
        ['Дата', 'Месяц', 'Источник', 'Сумма (₽)'],
        ...incomes.map((inc) => [
            inc.date,
            formatMonthLabel(inc.year, inc.month),
            inc.source,
            Math.round(Number(inc.amount) || 0),
        ]),
    ];

    const savingsRows = [
        ['Копилка', 'Накоплено (₽)', 'Цель (₽)', 'Прогресс (%)'],
        ...savingsGoals.map((g) => {
            const current = Math.round(Number(g.current_amount) || 0);
            const target = Math.round(Number(g.target_amount) || 0);
            const progress = target > 0 ? Math.min(100, Math.round((current / target) * 100)) : 0;
            return [g.name, current, target, progress];
        }),
    ];

    return { summaryRows, expenseRows, incomeRows, savingsRows };
}

function buildXlsxReport(reportData) {
    const { summaryRows, expenseRows, incomeRows, savingsRows } = buildReportTables(reportData);
    return generateXlsxBuffer([
        { name: 'Сводка', rows: summaryRows, columnWidths: [20, 16, 16, 16, 20] },
        { name: 'Расходы', rows: expenseRows, columnWidths: [14, 18, 24, 16, 36] },
        { name: 'Доходы', rows: incomeRows, columnWidths: [14, 18, 24, 16] },
        { name: 'Копилки', rows: savingsRows, columnWidths: [26, 18, 18, 16] },
    ]);
}

function buildCsvReport(reportData) {
    const tables = buildReportTables(reportData);
    return generateCsvBuffer(tables);
}

module.exports = {
    formatMonthLabel,
    buildXlsxReport,
    buildCsvReport,
};
