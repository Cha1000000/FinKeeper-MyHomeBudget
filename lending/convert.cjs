const sharp = require('sharp');
const fs = require('fs');
const path = require('path');

const assetsDir = path.join(__dirname, 'src', 'assets');

fs.readdirSync(assetsDir).forEach(file => {
  if (file.endsWith('.png')) {
    const inputPath = path.join(assetsDir, file);
    const outputPath = path.join(assetsDir, file.replace('.png', '.webp'));
    
    sharp(inputPath)
      .webp({ quality: 80 })
      .toFile(outputPath)
      .then(info => {
        console.log(`Converted ${file} to WebP (${info.size} bytes)`);
        // Remove original PNG
        fs.unlinkSync(inputPath);
      })
      .catch(err => {
        console.error(`Error converting ${file}:`, err);
      });
  }
});
