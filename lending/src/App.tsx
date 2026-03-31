import type { ReactNode } from 'react'
import { motion, useScroll, useTransform } from 'framer-motion'
import type { Variants } from 'framer-motion'
import type { LucideIcon } from 'lucide-react'
import { Wallet, PieChart, Activity, ArrowUpRight, ArrowDownRight, Target, Globe, Zap, Monitor, Smartphone, Download, ChevronRight, ShieldCheck, Lock } from 'lucide-react'
import imgWebDashboard from './assets/web-dashboard-dark.png'
import imgMobileDashboard from './assets/mobile-dashboard-1.png'
import imgDesktopApp from './assets/desktop-dashboard.png'
import imgMobileMonth from './assets/mobile-mont-dark.png'

const ASSETS = {
  webDashboard: imgWebDashboard,
  mobileDashboard: imgMobileDashboard,
  desktopApp: imgDesktopApp,
  mobileMonth: imgMobileMonth,
};

// Animations
const fadeUpVariants: Variants = {
  hidden: { opacity: 0, y: 30 },
  visible: { opacity: 1, y: 0, transition: { duration: 0.8, ease: 'easeOut' } }
}

const staggerContainer: Variants = {
  hidden: { opacity: 0 },
  visible: {
    opacity: 1,
    transition: { staggerChildren: 0.2 }
  }
}

const BentoCard = ({ children, className = "", span = "col-span-1" }: { children: ReactNode; className?: string; span?: string }) => (
  <motion.div 
    variants={fadeUpVariants}
    whileHover={{ y: -6, transition: { duration: 0.3, ease: 'easeOut' } }}
    className={`glass-panel p-6 sm:p-8 relative overflow-hidden group ${span} ${className}`}
  >
    <div className="absolute inset-0 bg-gradient-to-br from-emerald-base/0 via-emerald-base/0 to-emerald-base/10 opacity-0 group-hover:opacity-100 transition-opacity duration-700 rounded-3xl" />
    <div className="absolute -inset-px bg-gradient-to-br from-white/10 to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-700 rounded-3xl" />
    <div className="relative z-10 h-full flex flex-col">
      {children}
    </div>
  </motion.div>
);

const FeatureIcon = ({ icon: Icon, color = "text-emerald-base" }: { icon: LucideIcon; color?: string }) => (
    <div className={`w-12 h-12 rounded-xl bg-white/5 border border-white/10 flex items-center justify-center mb-6 ${color} shadow-lg transition-transform duration-500 group-hover:scale-110 group-hover:rotate-3`}>
    <Icon size={24} strokeWidth={1.5} />
  </div>
);

const PlatformLink = ({ icon: Icon, title, desc, url, primary = false }: { icon: LucideIcon; title: string; desc: string; url: string; primary?: boolean }) => (
  <motion.a 
    href={url}
    target="_blank"
    rel="noopener noreferrer"
    whileHover={{ scale: 1.02, y: -4 }}
    whileTap={{ scale: 0.98 }}
    className={`group flex items-center gap-5 p-4 sm:p-5 rounded-2xl border transition-all duration-500 h-full ${
      primary 
        ? 'bg-emerald-base/10 border-emerald-base/30 hover:bg-emerald-base/15 hover:border-emerald-base/50 shadow-[0_20px_40px_-15px_rgba(16,185,129,0.1)] hover:shadow-[0_30px_60px_-15px_rgba(16,185,129,0.25)]' 
        : 'bg-glass-bg border-glass-border hover:bg-white/5 hover:border-white/20 shadow-2xl shadow-black/20 hover:shadow-emerald-base/5'
    }`}
  >
    <div className={`p-3.5 rounded-xl flex-shrink-0 transition-all duration-500 group-hover:scale-110 group-hover:rotate-3 ${
      primary 
        ? 'bg-gradient-to-br from-emerald-400 to-emerald-600 text-main-bg shadow-[0_0_20px_rgba(16,185,129,0.4)]' 
        : 'bg-white/5 text-white border border-white/10 group-hover:border-emerald-base/30 group-hover:text-emerald-base'
    }`}>
      <Icon size={24} strokeWidth={primary ? 2.5 : 1.5} />
    </div>
    <div className="flex-1 min-w-0">
      <h4 className="font-display font-bold text-lg leading-tight mb-1 text-white group-hover:text-emerald-base transition-colors duration-300">{title}</h4>
      <p className="text-slate-text/70 text-[13px] leading-snug group-hover:text-slate-text transition-colors duration-300">{desc}</p>
    </div>
    <ChevronRight className={`transition-all duration-300 group-hover:translate-x-1 ${primary ? 'text-emerald-base' : 'text-slate-text/40 group-hover:text-emerald-base'}`} size={20} />
  </motion.a>
);

export default function App() {
  const { scrollY } = useScroll();
  const y1 = useTransform(scrollY, [0, 1000], [0, 200]);
  const y2 = useTransform(scrollY, [0, 1000], [0, -150]);

  // Download section parallax
  const downloadY1 = useTransform(scrollY, [1500, 3000], [0, -100]);
  const downloadY2 = useTransform(scrollY, [1500, 3000], [0, 80]);
  const downloadY3 = useTransform(scrollY, [1500, 3000], [0, 140]);
  const downloadX2 = useTransform(scrollY, [1500, 3000], [0, 100]);
  const downloadX3 = useTransform(scrollY, [1500, 3000], [0, -80]);

  return (
    <div className="min-h-screen relative font-body bg-main-bg text-white overflow-x-hidden">
      {/* Background Orbs */}
      <div className="fixed inset-0 pointer-events-none z-0">
        <motion.div style={{ y: y1 }} className="absolute -top-[20%] -left-[10%] w-[50vw] h-[50vw] rounded-full bg-emerald-glow/20 blur-[120px] mix-blend-screen opacity-50 animate-pulse-slow" />
        <motion.div style={{ y: y2 }} className="absolute top-[40%] -right-[20%] w-[60vw] h-[60vw] rounded-full bg-blue-900/20 blur-[150px] mix-blend-screen opacity-40 animate-pulse-slow" />
        <div className="absolute -bottom-[20%] left-[20%] w-[40vw] h-[40vw] rounded-full bg-emerald-base/10 blur-[100px] mix-blend-screen opacity-30" />
      </div>

      {/* Navigation */}
      <nav className="fixed top-0 inset-x-0 z-50 px-6 py-4">
        <div className="max-w-6xl mx-auto flex items-center justify-between">
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-lg bg-emerald-base flex items-center justify-center">
              <Wallet className="text-main-bg" size={18} fill="currentColor" />
            </div>
            <span className="font-display font-semibold text-xl tracking-tight">FinKeeper24: Моя домашняя бухгалтерия</span>
          </div>
          <a href="#download" className="hidden sm:inline-flex items-center justify-center px-5 py-2.5 rounded-full bg-white/10 hover:bg-white/20 border border-white/5 backdrop-blur-md transition-all font-display font-medium text-sm">
            Скачать приложение
          </a>
        </div>
      </nav>

      <main className="relative z-10">
        {/* Hero Section */}
        <section className="pt-8 pb-6 md:pt-12 md:pb-8 lg:pt-14 lg:pb-10 px-6 min-h-[85vh] lg:min-h-screen flex flex-col justify-center overflow-hidden relative">
          <div className="max-w-7xl mx-auto w-full relative z-30 flex flex-col items-center text-center">
            <motion.div 
              initial="hidden"
              animate="visible"
              variants={staggerContainer}
              className="max-w-4xl"
            >
              <div className="inline-flex items-center gap-2 px-4 py-2 rounded-full bg-emerald-base/5 border border-emerald-base/10 text-emerald-base text-[10px] font-display font-black mb-8 tracking-[0.25em] uppercase backdrop-blur-xl shadow-2xl shadow-emerald-base/5">
                <span className="relative flex h-2 w-2">
                  <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-base opacity-75"></span>
                  <span className="relative inline-flex rounded-full h-2 w-2 bg-emerald-base"></span>
                </span>
                v2.0.0 • PRO SYSTEM
              </div>
              
              <motion.h1 variants={fadeUpVariants} className="font-display text-4xl sm:text-5xl md:text-5xl lg:text-6xl xl:text-[64px] font-black leading-[1.05] mb-5 md:mb-6 tracking-[-0.04em] text-white">
                Ваши финансы <br />
                <span className="text-transparent bg-clip-text bg-gradient-to-r from-emerald-300 via-emerald-base to-emerald-500 text-glow">
                  В идеальном балансе
                </span>
              </motion.h1>
              
              <motion.p variants={fadeUpVariants} className="text-slate-text/90 text-base sm:text-lg font-light leading-relaxed mb-10 max-w-2xl mx-auto">
                Ваш надежный компаньон в мире личных финансов. Отслеживайте расходы, управляйте бюджетами и достигайте целей в стильном интерфейсе. Доступно на всех ваших устройствах с мгновенной синхронизацией.
              </motion.p>
              
              <motion.div variants={fadeUpVariants} className="flex flex-wrap items-center justify-center gap-6">
                <a href="https://app.finkeeper24.ru" target="_blank" rel="noopener noreferrer" className="px-10 py-5 rounded-2xl bg-emerald-base text-main-bg font-display font-bold hover:bg-emerald-400 transition-all shadow-[0_20px_40px_-10px_rgba(16,185,129,0.3)] hover:-translate-y-1 flex items-center gap-3">
                  Начать планирование бюджета <ArrowUpRight size={22} />
                </a>
                <a href="#download" className="px-10 py-5 rounded-2xl bg-white/5 border border-white/10 hover:bg-white/10 transition-all font-display font-medium flex items-center gap-3 backdrop-blur-md">
                  Загрузить приложение <Download size={22} />
                </a>
              </motion.div>
            </motion.div>

            {/* Premium Device Composition (Wealthly Style) */}
            <motion.div 
              initial={{ opacity: 0, y: 100 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 1.5, ease: [0.22, 1, 0.36, 1], delay: 0.5 }}
              className="mt-2 md:mt-4 lg:mt-6 relative w-full max-w-4xl lg:max-w-[950px] aspect-[16/14] md:aspect-[16/16] perspective-2000"
            >
              {/* Web Dashboard Mockup */}
              <motion.div 
                style={{ rotateX: 10, y: useTransform(scrollY, [0, 1000], [0, 150]) }}
                className="absolute inset-x-0 top-0 mx-auto w-[94%] aspect-[16/14] md:aspect-[16/16] glass-panel border-white/20 shadow-[0_50px_100px_-20px_rgba(0,0,0,0.9)] overflow-hidden z-10 rounded-2xl md:rounded-3xl"
              >
                <div className="absolute inset-0 bg-emerald-base/5 z-0" />
                <div className="relative z-10 p-2 md:p-4 h-full flex flex-col">
                  <div className="flex items-center justify-between mb-2 md:mb-4 px-2">
                    <div className="flex gap-1.5 md:gap-2">
                      <div className="w-2 h-2 md:w-3 md:h-3 rounded-full bg-rose-flash/40" />
                      <div className="w-2 h-2 md:w-3 md:h-3 rounded-full bg-amber-500/40" />
                      <div className="w-2 h-2 md:w-3 md:h-3 rounded-full bg-emerald-base/40" />
                    </div>
                  </div>
                  <img src={ASSETS.webDashboard} alt="Web System" className="w-full h-full object-cover object-top rounded-lg md:rounded-xl shadow-2xl" />
                </div>
              </motion.div>

              {/* Floating Mobile Focal Point */}
              <motion.div 
                animate={{ 
                  y: [-20, 20, -20],
                  rotate: [-5, -2, -5]
                }}
                transition={{ duration: 8, repeat: Infinity, ease: "easeInOut" }}
                className="absolute -bottom-10 -left-10 md:left-0 w-[180px] md:w-[280px] aspect-[9/19] glass-panel border-white/30 shadow-[0_40px_80px_-20px_rgba(0,0,0,1)] z-40 rounded-[32px] md:rounded-[56px] p-2 md:p-3 overflow-hidden"
              >
                <div className="w-full h-full rounded-[24px] md:rounded-[36px] overflow-hidden relative bg-black border border-white/5">
                  <img src={ASSETS.mobileDashboard} alt="Mobile App" className="w-full h-full object-cover" />
                </div>
              </motion.div>

              {/* Decorative Accent: Month View */}
              <motion.div 
                animate={{ 
                  y: [20, -20, 20],
                  rotate: [8, 12, 8]
                }}
                transition={{ duration: 10, repeat: Infinity, ease: "easeInOut", delay: 1 }}
                className="absolute top-20 -right-5 md:right-10 w-[140px] md:w-[220px] aspect-[9/19] glass-panel border-white/10 shadow-2xl z-20 rounded-[24px] md:rounded-[40px] p-2 overflow-hidden opacity-60 blur-[0.5px] group-hover:opacity-100 group-hover:blur-0 transition-all duration-700"
              >
                <div className="w-full h-full rounded-[16px] md:rounded-[32px] overflow-hidden relative bg-black border border-white/5">
                  <img src={ASSETS.mobileMonth} alt="Analytics" className="w-full h-full object-cover" />
                </div>
              </motion.div>

              {/* Professional Widget: Performance */}
              <motion.div 
                animate={{ y: [0, -30, 0] }}
                transition={{ duration: 7, repeat: Infinity, ease: "easeInOut" }}
                className="absolute top-1/2 -right-6 md:-right-12 z-50 p-4 md:p-6 glass-panel border-emerald-base/40 shadow-[0_30px_60px_-15px_rgba(16,185,129,0.3)] backdrop-blur-3xl min-w-[180px] md:min-w-[240px]"
              >
                <div className="flex flex-col gap-4 md:gap-5">
                  <div className="flex items-center justify-between">
                    <div className="text-[8px] md:text-[10px] text-emerald-base uppercase tracking-[0.3em] font-mono font-black">Эффективность</div>
                    <div className="w-6 h-6 md:w-8 md:h-8 rounded-full bg-emerald-base/20 flex items-center justify-center text-emerald-base border border-emerald-base/20">
                      <Activity size={14} />
                    </div>
                  </div>
                  <div className="flex flex-col">
                    <div className="text-2xl md:text-3xl font-display font-black text-white tracking-tighter">+12.4%</div>
                    <div className="text-[8px] md:text-[10px] text-slate-text uppercase font-mono">Прирост за месяц</div>
                  </div>
                  <div className="flex gap-1 md:gap-2 h-4 md:h-6 items-end">
                    {[30, 60, 45, 90, 65, 80, 50, 40, 70, 55, 85, 45].map((h, i) => (
                      <motion.div 
                        key={i} 
                        initial={{ height: 0 }}
                        animate={{ height: `${h}%` }}
                        transition={{ delay: i * 0.04, duration: 1.2 }}
                        className="flex-1 bg-emerald-base/40 rounded-full" 
                      />
                    ))}
                  </div>
                </div>
              </motion.div>
            </motion.div>
          </div>
          
          {/* Hero Decorative Background */}
          <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-full h-full -z-10 overflow-hidden pointer-events-none">
            <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[150%] h-[150%] bg-emerald-glow/5 blur-[200px] rounded-full" />
            <div className="absolute inset-0 bg-gradient-to-b from-transparent via-main-bg/50 to-main-bg" />
          </div>
        </section>

        {/* Bento Features Section */}
        <section className="py-32 px-6 relative z-10">
          <div className="max-w-7xl mx-auto">
            <motion.div 
              initial="hidden"
              whileInView="visible"
              viewport={{ once: true, margin: "-100px" }}
              variants={staggerContainer}
              className="text-left mb-10"
            >
              <motion.h2 variants={fadeUpVariants} className="font-display text-4xl sm:text-5xl md:text-5xl lg:text-6xl xl:text-[64px] font-bold mb-6 tracking-tight text-white">
                Всё под <span className="text-emerald-base text-glow">контролем</span>
              </motion.h2>
              <motion.p variants={fadeUpVariants} className="text-slate-text text-xl max-w-2xl font-light">
                Инструменты для тех, кто ценит точность и удобство. Профессиональная аналитика в интуитивном формате.
              </motion.p>
            </motion.div>

            <motion.div 
              initial="hidden"
              whileInView="visible"
              viewport={{ once: true, margin: "-50px" }}
              variants={staggerContainer}
              className="grid grid-cols-1 md:grid-cols-4 gap-6"
            >
              {/* Row 1 */}
              <BentoCard span="md:col-span-2" className="glass-panel-hover">
                <div className="flex flex-col h-full">
                  <FeatureIcon icon={PieChart} />
                  <h3 className="font-display text-2xl mb-4 text-white">Бюджеты по месяцам</h3>
                  <p className="text-slate-text leading-relaxed font-light mb-8">
                    Разделение доходов и расходов на периоды. Устанавливайте лимиты по категориям на каждый месяц отдельно.
                  </p>
                  <div className="mt-auto relative w-full h-48 overflow-hidden rounded-2xl border border-white/10 bg-black/40 p-6">
                    <div className="flex flex-col gap-5">
                      {[
                        { label: 'Продукты', spent: 12000, limit: 15000, color: 'from-emerald-base to-emerald-400', progress: 80 },
                        { label: 'Транспорт', spent: 4500, limit: 5000, color: 'from-amber-400 to-orange-400', progress: 90 },
                        { label: 'Развлечения', spent: 8000, limit: 7000, color: 'from-rose-flash to-rose-600', progress: 114 }
                      ].map((item, i) => (
                        <div key={i} className="space-y-2">
                          <div className="flex justify-between items-end text-[10px] font-mono uppercase tracking-[0.1em] text-slate-text/80">
                            <span className="flex items-center gap-2">
                              <span className={`w-1 h-1 rounded-full bg-gradient-to-r ${item.color}`} />
                              {item.label}
                            </span>
                            <span className="text-white font-bold">{item.spent.toLocaleString()} / {item.limit.toLocaleString()} ₽</span>
                          </div>
                          <div className="h-1.5 w-full bg-white/5 rounded-full overflow-hidden relative">
                            <motion.div 
                              initial={{ width: 0 }}
                              whileInView={{ width: `${Math.min(item.progress, 100)}%` }}
                              transition={{ duration: 1.2, delay: i * 0.1, ease: [0.22, 1, 0.36, 1] }}
                              className={`h-full bg-gradient-to-r ${item.color} relative z-10`}
                            />
                            {item.progress > 100 && (
                              <motion.div 
                                initial={{ opacity: 0 }}
                                whileInView={{ opacity: 1 }}
                                className="absolute top-0 right-0 h-full bg-rose-flash/20 w-[30%] blur-sm z-0"
                              />
                            )}
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                </div>
              </BentoCard>

              <BentoCard span="md:col-span-2" className="glass-panel-hover">
                <div className="flex flex-col h-full">
                  <FeatureIcon icon={Target} color="text-indigo-accent" />
                  <h3 className="font-display text-2xl mb-4 text-white">Умные Копилки</h3>
                  <p className="text-slate-text leading-relaxed font-light mb-8">
                    Достигайте своих целей. Пополнения автоматически вычитаются из доступного бюджета месяца, чтобы вы не потратили лишнего.
                  </p>
                  <div className="mt-auto relative w-full h-48 overflow-hidden rounded-2xl border border-white/10 bg-black/40 p-6 flex items-center justify-center">
                    <div className="relative w-36 h-36">
                      <svg className="w-full h-full -rotate-90" viewBox="0 0 100 100">
                        <circle 
                          className="text-white/5 stroke-current" 
                          strokeWidth="6" 
                          cx="50" cy="50" r="42" 
                          fill="transparent" 
                        />
                        <motion.circle 
                          className="text-indigo-accent stroke-current" 
                          strokeWidth="6" 
                          strokeLinecap="round" 
                          cx="50" cy="50" r="42" 
                          fill="transparent"
                          initial={{ pathLength: 0 }}
                          whileInView={{ pathLength: 0.7 }}
                          transition={{ duration: 2, ease: [0.22, 1, 0.36, 1] }}
                          style={{ filter: 'drop-shadow(0 0 8px rgba(99, 102, 241, 0.5))' }}
                        />
                        {/* Secondary track for secondary goal or projection */}
                        <circle 
                          className="text-white/5 stroke-current" 
                          strokeWidth="2" 
                          cx="50" cy="50" r="32" 
                          fill="transparent" 
                        />
                        <motion.circle 
                          className="text-emerald-base/40 stroke-current" 
                          strokeWidth="2" 
                          strokeLinecap="round" 
                          cx="50" cy="50" r="32" 
                          fill="transparent"
                          initial={{ pathLength: 0 }}
                          whileInView={{ pathLength: 0.4 }}
                          transition={{ duration: 2.5, ease: [0.22, 1, 0.36, 1], delay: 0.5 }}
                        />
                      </svg>
                      <div className="absolute inset-0 flex flex-col items-center justify-center translate-y-1">
                        <span className="text-3xl font-display font-black text-white tracking-tighter">70%</span>
                        <span className="text-[9px] font-mono text-slate-text uppercase tracking-widest">Цель: Отпуск</span>
                      </div>
                    </div>
                  </div>
                </div>
              </BentoCard>

              {/* Row 2 */}
              <BentoCard span="md:col-span-1" className="bg-emerald-base/5 glass-panel-hover overflow-hidden">
                <div className="absolute top-0 right-0 p-3 opacity-20 group-hover:opacity-100 transition-all duration-500 group-hover:rotate-12">
                  <div className="w-20 h-20 rounded-full border border-emerald-base/20 flex items-center justify-center">
                    <div className="w-12 h-12 rounded-full border border-emerald-base/40 animate-pulse" />
                  </div>
                </div>
                <FeatureIcon icon={Activity} color="text-emerald-base" />
                <h3 className="font-display text-xl mb-3 text-white">Наглядная аналитика</h3>
                <p className="text-slate-text text-sm leading-relaxed font-light mb-6">
                  Подробные дешборды, круговые диаграммы структуры расходов и графики трендов.
                </p>
                <div className="mt-auto flex items-end gap-2 h-24 relative px-2">
                  {[40, 70, 45, 90, 65, 80, 50, 85, 60, 95, 55, 75].map((h, i) => (
                    <div key={i} className="flex-1 flex flex-col justify-end h-full group/bar">
                      <motion.div 
                        initial={{ height: 0 }}
                        whileInView={{ height: `${h}%` }}
                        transition={{ duration: 1.2, delay: i * 0.04, ease: [0.22, 1, 0.36, 1] }}
                        className="w-full bg-gradient-to-t from-emerald-base/40 to-emerald-base/10 rounded-t-[2px] relative group-hover/bar:from-emerald-base/80 transition-all duration-300"
                      >
                        <div className="absolute -top-6 left-1/2 -translate-x-1/2 opacity-0 group-hover/bar:opacity-100 transition-opacity bg-emerald-base text-main-bg text-[8px] font-bold px-1 rounded-sm pointer-events-none">
                          {h}%
                        </div>
                      </motion.div>
                    </div>
                  ))}
                  <div className="absolute -bottom-2 left-0 right-0 h-[1px] bg-white/10" />
                </div>
              </BentoCard>

              <BentoCard span="md:col-span-2" className="glass-panel-hover overflow-hidden">
                <div className="flex flex-col sm:flex-row gap-8 h-full">
                  <div className="flex-1">
                    <FeatureIcon icon={Zap} color="text-amber-400" />
                    <h3 className="font-display text-2xl mb-4 text-white">Занимает секунды</h3>
                    <p className="text-slate-text leading-relaxed font-light">
                      Добавление покупки происходит быстрее, чем вы получите сдачу. Интерфейс спроектирован так, чтобы не отвлекать вас от жизни.
                    </p>
                  </div>
                  <div className="flex flex-col gap-3 justify-center relative py-4">
                    <div className="absolute -inset-10 bg-amber-400/5 blur-[60px] rounded-full pointer-events-none" />
                    {[
                      { icon: ArrowUpRight, color: 'text-rose-flash', val: '- 450 ₽', label: 'Кофе', cat: 'Еда', delay: 0 },
                      { icon: ArrowDownRight, color: 'text-emerald-base', val: '+ 1 500 ₽', label: 'Перевод', cat: 'Доход', delay: 0.1 },
                      { icon: ArrowUpRight, color: 'text-rose-flash', val: '- 120 ₽', label: 'Метро', cat: 'Транспорт', delay: 0.2 },
                    ].map((tx, i) => (
                      <motion.div 
                        key={i}
                        initial={{ opacity: 0, x: 30, filter: 'blur(10px)' }}
                        whileInView={{ opacity: 1, x: 0, filter: 'blur(0px)' }}
                        transition={{ delay: tx.delay, duration: 0.8, ease: [0.22, 1, 0.36, 1] }}
                        whileHover={{ x: -10, backgroundColor: 'rgba(255,255,255,0.08)', borderColor: 'rgba(255,255,255,0.2)' }}
                        className="p-3.5 rounded-2xl bg-white/5 border border-white/10 flex items-center gap-4 min-w-[220px] shadow-xl backdrop-blur-md relative z-10 transition-colors"
                      >
                        <div className={`w-10 h-10 rounded-xl bg-white/5 flex items-center justify-center ${tx.color} border border-white/5 shadow-inner`}>
                          <tx.icon size={18} />
                        </div>
                        <div className="flex-1">
                          <div className="flex justify-between items-center mb-1">
                            <div className="text-[10px] text-slate-text/60 font-medium uppercase tracking-wider">{tx.label}</div>
                            <div className="text-[8px] px-1.5 py-0.5 rounded-full bg-white/5 text-slate-text/40 border border-white/5">{tx.cat}</div>
                          </div>
                          <div className="font-mono text-sm font-black text-white">{tx.val}</div>
                        </div>
                      </motion.div>
                    ))}
                  </div>
                </div>
              </BentoCard>

              <BentoCard span="md:col-span-1" className="glass-panel-hover">
                <div className="flex flex-col h-full">
                  <FeatureIcon icon={Globe} color="text-blue-400" />
                  <h3 className="font-display text-xl mb-3 text-white">Всегда под рукой</h3>
                  <p className="text-slate-text text-sm leading-relaxed font-light mb-6">
                    Записывайте расходы даже там, где нет связи. Все синхронизируется само.
                  </p>
                  <div className="mt-auto flex items-center gap-3 px-4 py-4 rounded-xl bg-blue-400/5 border border-blue-400/20 w-full group/sync relative overflow-hidden">
                    <div className="absolute inset-0 bg-blue-400/5 opacity-0 group-hover/sync:opacity-100 transition-opacity" />
                    <motion.div 
                      animate={{ rotate: 360 }}
                      transition={{ duration: 8, repeat: Infinity, ease: "linear" }}
                      className="text-blue-400 relative z-10"
                    >
                      <Activity size={16} />
                    </motion.div>
                    <div className="flex-1 relative z-10">
                      <div className="text-[10px] font-mono text-blue-400 uppercase tracking-widest font-bold flex justify-between">
                        <span>Sync Active</span>
                        <span className="opacity-50">v1.2</span>
                      </div>
                      <div className="w-full h-1.5 bg-blue-400/10 rounded-full mt-2 overflow-hidden">
                        <motion.div 
                          animate={{ x: ['-100%', '100%'] }}
                          transition={{ duration: 2, repeat: Infinity, ease: "linear" }}
                          className="w-1/2 h-full bg-gradient-to-r from-transparent via-blue-400/60 to-transparent"
                        />
                      </div>
                    </div>
                  </div>
                </div>
              </BentoCard>

              {/* Row 3 */}
              <BentoCard span="md:col-span-4" className="bg-gradient-to-r from-emerald-base/5 via-emerald-base/0 to-transparent border-emerald-base/20">
                <div className="flex flex-col md:flex-row items-center gap-12 py-4">
                  <div className="flex-1 text-left">
                    <FeatureIcon icon={Lock} color="text-emerald-base" />
                    <h3 className="font-display text-3xl mb-4 leading-tight text-white">Ваши данные — <br className="hidden lg:block"/> только ваши</h3>
                    <p className="text-slate-text text-lg leading-relaxed font-light max-w-xl">
                      Мы ценим вашу приватность. Никаких облачных хранилищ без вашего ведома и возможность в любой момент забрать свою историю. Полная анонимность и безопасность.
                    </p>
                  </div>
                  <div className="flex flex-col sm:flex-row gap-6">
                    <div className="hidden lg:block relative w-56 h-36 rounded-2xl border border-white/10 group-hover:border-emerald-base/20 transition-all duration-500 shadow-2xl overflow-hidden bg-black/40 backdrop-blur-md isolate">
                      <div className="absolute inset-0 bg-gradient-to-br from-emerald-base/20 to-transparent z-10 pointer-events-none" />
                      <div className="absolute inset-0 flex items-center justify-center z-0">
                         <ShieldCheck className="text-emerald-base" size={48} strokeWidth={1.5} />
                      </div>
                      <div className="absolute bottom-4 left-4 right-4 h-1.5 bg-white/5 rounded-full overflow-hidden">
                        <motion.div 
                          animate={{ x: ['-100%', '100%'] }}
                          transition={{ duration: 3, repeat: Infinity, ease: "linear" }}
                          className="w-1/2 h-full bg-emerald-base/40"
                        />
                      </div>
                    </div>
                  </div>
                </div>
              </BentoCard>
            </motion.div>
          </div>
        </section>

        {/* Download & Platforms Section */}
        <section id="download" className="py-24 md:py-32 lg:py-40 px-6 relative z-10 overflow-hidden">
          {/* Background Highlight */}
          <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[80%] h-[80%] bg-indigo-600/5 blur-[120px] rounded-full -z-10" />
          
          <div className="max-w-7xl mx-auto">
            <div className="grid lg:grid-cols-2 gap-24 items-center">
              
              <motion.div 
                initial="hidden"
                whileInView="visible"
                viewport={{ once: true }}
                variants={staggerContainer}
              >
                <motion.h2 variants={fadeUpVariants} className="font-display text-4xl sm:text-5xl md:text-5xl lg:text-6xl xl:text-[64px] font-bold mb-6 md:mb-8 leading-[1.05] text-center lg:text-left text-white tracking-tight">
                  Ваши финансы <br />
                  <span className="text-emerald-base text-glow">на любом устройстве</span>
                </motion.h2>
                <motion.p variants={fadeUpVariants} className="text-slate-text text-xl mb-12 font-light leading-relaxed text-center lg:text-left max-w-xl mx-auto lg:mx-0">
                  Мы создали FinKeeper24 так, чтобы он был доступен везде. Ваша бухгалтерия мгновенно синхронизируется между смартфоном, планшетом и компьютером.
                </motion.p>
                
                <motion.div variants={staggerContainer} className="space-y-4">
                  <PlatformLink 
                    primary
                    icon={Monitor} 
                    title="Web-версия" 
                    desc="Мгновенный доступ через любой современный браузер" 
                    url="https://app.finkeeper24.ru" 
                  />
                  <div className="grid sm:grid-cols-2 gap-4 items-stretch">
                    <PlatformLink 
                      icon={Smartphone} 
                      title="Android" 
                      desc="RuStore • APK • Play Store" 
                      url="#" 
                    />
                    <PlatformLink 
                      icon={Monitor} 
                      title="Desktop" 
                      desc="Windows • macOS • Linux" 
                      url="#" 
                    />
                  </div>
                </motion.div>
              </motion.div>

              <motion.div 
                initial={{ opacity: 0, scale: 0.9 }}
                whileInView={{ opacity: 1, scale: 1 }}
                viewport={{ once: true }}
                className="relative min-h-[600px] flex items-center justify-center group/download perspective-2000"
              >
                {/* Background Glow */}
                <div className="absolute inset-0 bg-emerald-base/5 blur-[120px] rounded-full opacity-50" />
                
                {/* Device Ecosystem Composition */}
                <div className="relative z-10 w-full aspect-square max-w-[500px]">
                  {/* Desktop App (Back Layer) */}
                  <motion.div
                    style={{ y: downloadY1 }}
                    className="absolute inset-0 glass-panel border-white/10 shadow-[0_80px_160px_-40px_rgba(0,0,0,0.9)] overflow-hidden rounded-3xl z-0"
                  >
                    <img src={ASSETS.desktopApp} alt="Desktop App" className="w-full h-full object-cover" />
                    <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-transparent to-transparent" />
                  </motion.div>

                  {/* Web Dashboard (Middle Layer) */}
                  <motion.div
                    style={{ 
                      x: downloadX2,
                      y: downloadY2
                    }}
                    className="absolute top-[15%] -right-12 w-[110%] aspect-video glass-panel border-white/20 shadow-[0_100px_200px_-50px_rgba(0,0,0,0.95)] overflow-hidden rounded-2xl z-10"
                  >
                    <img src={ASSETS.webDashboard} alt="Web App" className="w-full h-full object-cover" />
                    <div className="absolute inset-0 bg-emerald-base/5 backdrop-brightness-110" />
                  </motion.div>

                  {/* Mobile App (Front Layer) */}
                  <motion.div
                    style={{ 
                      x: downloadX3,
                      y: downloadY3,
                      rotate: -5
                    }}
                    className="absolute -bottom-10 -left-10 w-1/3 aspect-[9/19] glass-panel border-white/30 shadow-[0_40px_80px_-20px_rgba(0,0,0,0.8)] rounded-[24px] md:rounded-[40px] overflow-hidden z-20 p-2 md:p-3"
                  >
                    <div className="w-full h-full rounded-[16px] md:rounded-[10px] overflow-hidden relative bg-black border border-white/5">
                      <img src={ASSETS.mobileDashboard} alt="Mobile App" className="w-full h-full object-cover" />
                    </div>
                  </motion.div>

                  {/* UI Fragment: Month View (Floating) */}
                  <motion.div
                    animate={{ y: [0, -30, 0] }}
                    transition={{ duration: 5, repeat: Infinity, ease: "easeInOut" }}
                    className="absolute -top-16 right-0 w-1/4 aspect-[9/19] glass-panel border-white/10 shadow-xl rounded-[24px] z-30 opacity-80 blur-[0.5px] hover:blur-0 hover:opacity-100 transition-all p-1.5"
                  >
                    <div className="w-full h-full rounded-[16px] overflow-hidden relative bg-black border border-white/5">
                      <img src={ASSETS.mobileMonth} alt="Mobile Month" className="w-full h-full object-cover" />
                    </div>
                  </motion.div>
                </div>
              </motion.div>
            </div>
          </div>
        </section>
      </main>

      <footer className="py-10 px-6 mt-10 border-t border-white/10 text-center relative z-10">
        <p className="text-slate-text/70 text-sm font-mono">&copy; {new Date().getFullYear()} FinKeeper24: Моя домашняя бухгалтерия. Все права защищены.</p>
      </footer>
    </div>
  )
}
