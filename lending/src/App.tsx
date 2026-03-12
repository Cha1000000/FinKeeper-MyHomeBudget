import React from 'react'
import { motion, useScroll, useTransform } from 'framer-motion'
import type { Variants } from 'framer-motion'
import { Wallet, PieChart, ShieldCheck, Download, Smartphone, LayoutDashboard, Monitor, ChevronRight, Activity, ArrowUpRight, ArrowDownRight, Target } from 'lucide-react'

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

const GlassCard = ({ children, className = "" }: { children: React.ReactNode, className?: string }) => (
  <motion.div 
    variants={fadeUpVariants}
    whileHover={{ y: -6, transition: { duration: 0.3, ease: 'easeOut' } }}
    className={`glass-panel p-6 sm:p-8 relative overflow-hidden group ${className}`}
  >
    {/* Hover Glow Effect */}
    <div className="absolute inset-0 bg-gradient-to-br from-emerald-base/0 via-emerald-base/0 to-emerald-base/5 opacity-0 group-hover:opacity-100 transition-opacity duration-500 rounded-3xl" />
    <div className="relative z-10">
      {children}
    </div>
  </motion.div>
);

const FeatureItem = ({ icon: Icon, title, description }: { icon: any, title: string, description: string }) => (
  <GlassCard>
    <div className="w-12 h-12 rounded-xl bg-emerald-base/20 border border-emerald-base/30 flex items-center justify-center mb-6 text-emerald-base shadow-[0_0_15px_rgba(16,185,129,0.2)]">
      <Icon size={24} strokeWidth={1.5} />
    </div>
    <h3 className="font-unbounded text-xl mb-3">{title}</h3>
    <p className="text-slate-text leading-relaxed font-light">{description}</p>
  </GlassCard>
);

const PlatformLink = ({ icon: Icon, title, desc, url, primary = false }: { icon: any, title: string, desc: string, url: string, primary?: boolean }) => (
  <motion.a 
    href={url}
    target="_blank"
    rel="noopener noreferrer"
    whileHover={{ scale: 1.02 }}
    whileTap={{ scale: 0.98 }}
    className={`flex items-center gap-5 p-4 sm:p-5 rounded-2xl border transition-all duration-300 ${
      primary 
        ? 'bg-emerald-base/10 border-emerald-base/40 hover:bg-emerald-base/20 hover:border-emerald-base/60' 
        : 'bg-glass-bg border-glass-border hover:bg-white/10 hover:border-white/20'
    }`}
  >
    <div className={`p-3 rounded-xl flex-shrink-0 ${primary ? 'bg-emerald-base text-main-bg' : 'bg-white/10 text-white'}`}>
      <Icon size={24} />
    </div>
    <div className="flex-1">
      <h4 className="font-unbounded font-medium text-lg leading-tight mb-1">{title}</h4>
      <p className="text-slate-text text-sm">{desc}</p>
    </div>
    <ChevronRight className={primary ? 'text-emerald-base' : 'text-slate-text'} />
  </motion.a>
);

export default function App() {
  const { scrollY } = useScroll();
  const y1 = useTransform(scrollY, [0, 1000], [0, 200]);
  const y2 = useTransform(scrollY, [0, 1000], [0, -150]);

  return (
    <div className="min-h-screen relative font-inter overflow-hidden">
      {/* Background Orbs */}
      <div className="fixed inset-0 pointer-events-none z-0">
        <motion.div style={{ y: y1 }} className="absolute -top-[20%] -left-[10%] w-[50vw] h-[50vw] rounded-full bg-emerald-glow/20 blur-[120px] mix-blend-screen opacity-50 animate-pulse-slow" />
        <motion.div style={{ y: y2, animationDelay: '-4s' }} className="absolute top-[40%] -right-[20%] w-[60vw] h-[60vw] rounded-full bg-blue-900/20 blur-[150px] mix-blend-screen opacity-40 animate-pulse-slow" />
        <div className="absolute -bottom-[20%] left-[20%] w-[40vw] h-[40vw] rounded-full bg-emerald-base/10 blur-[100px] mix-blend-screen opacity-30" />
      </div>

      {/* Navigation */}
      <nav className="fixed top-0 inset-x-0 z-50 px-6 py-4">
        <div className="max-w-6xl mx-auto flex items-center justify-between">
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-lg bg-emerald-base flex items-center justify-center">
              <Wallet className="text-main-bg" size={18} fill="currentColor" />
            </div>
            <span className="font-unbounded font-semibold text-xl tracking-tight">FinKeeper</span>
          </div>
          <a href="#download" className="hidden sm:inline-flex items-center justify-center px-5 py-2.5 rounded-full bg-white/10 hover:bg-white/20 border border-white/5 backdrop-blur-md transition-all font-medium text-sm">
            Скачать приложение
          </a>
        </div>
      </nav>

      <main className="relative z-10">
        {/* Hero Section */}
        <section className="pt-40 pb-20 md:pt-52 md:pb-32 px-6 min-h-[90vh] flex flex-col justify-center">
          <div className="max-w-6xl mx-auto grid lg:grid-cols-2 gap-12 lg:gap-8 items-center">
            
            <motion.div 
              initial="hidden"
              animate="visible"
              variants={staggerContainer}
              className="max-w-xl"
            >
              <div className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full bg-emerald-base/10 border border-emerald-base/20 text-emerald-base text-sm font-medium mb-8">
                <span className="relative flex h-2 w-2">
                  <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-base opacity-75"></span>
                  <span className="relative inline-flex rounded-full h-2 w-2 bg-emerald-base"></span>
                </span>
                FinKeeper 1.5.0 доступен
              </div>
              
              <motion.h1 variants={fadeUpVariants} className="font-unbounded text-5xl sm:text-6xl md:text-7xl font-bold leading-[1.1] mb-6">
                Ваши финансы <br className="hidden sm:block" />
                <span className="text-transparent bg-clip-text bg-gradient-to-r from-emerald-400 to-emerald-base text-glow">
                  как на ладони.
                </span>
              </motion.h1>
              
              <motion.p variants={fadeUpVariants} className="text-slate-text text-lg sm:text-xl font-light leading-relaxed mb-10 max-w-lg">
                Возьмите контроль над своими деньгами. Замените устаревшие таблицы Excel на современный, технологичный инструмент для достижения финансовой независимости.
              </motion.p>
              
              <motion.div variants={fadeUpVariants} className="flex flex-wrap items-center gap-4">
                <a href="http://217.114.8.82:3002/" target="_blank" rel="noopener noreferrer" className="px-8 py-4 rounded-2xl bg-emerald-base text-main-bg font-unbounded font-medium hover:bg-emerald-400 transition-colors shadow-[0_0_20px_rgba(16,185,129,0.4)] flex items-center gap-2">
                  Открыть Web-версию <ArrowUpRight size={20} />
                </a>
                <a href="#download" className="px-8 py-4 rounded-2xl bg-glass-bg border border-glass-border hover:bg-white/10 transition-colors font-medium flex items-center gap-2">
                  Скачать приложение <Download size={20} />
                </a>
              </motion.div>
            </motion.div>

            {/* Hero Mockup */}
            <motion.div 
              initial={{ opacity: 0, scale: 0.9, x: 20 }}
              animate={{ opacity: 1, scale: 1, x: 0 }}
              transition={{ duration: 1, ease: [0.22, 1, 0.36, 1], delay: 0.2 }}
              className="relative lg:h-[600px] flex items-center justify-center animate-float lg:justify-end"
            >
              {/* App UI Concept snippet rendered visually */}
              <div className="relative w-full max-w-md aspect-[4/5] sm:aspect-square lg:aspect-[3/4] glass-panel p-6 flex flex-col border border-white/20 shadow-2xl overflow-hidden before:absolute before:inset-0 before:bg-gradient-to-br before:from-emerald-base/10 before:to-transparent before:opacity-50">
                
                {/* Mock Header */}
                <div className="flex items-center justify-between mb-8 relative z-10">
                  <div>
                    <div className="text-slate-text text-sm mb-1">Доступный остаток</div>
                    <div className="font-mono text-3xl font-bold tracking-tight">45 200 ₽</div>
                  </div>
                  <div className="w-10 h-10 rounded-full bg-gradient-to-br from-emerald-400 to-emerald-glow" />
                </div>

                {/* Mock Chart Area */}
                <div className="relative h-40 mb-8 z-10">
                  <svg className="w-full h-full" viewBox="0 0 300 100" preserveAspectRatio="none">
                    <path d="M0,100 L0,50 C50,40 80,80 150,50 C220,20 250,60 300,10 L300,100 Z" fill="url(#gradient)" opacity="0.2" />
                    <path d="M0,50 C50,40 80,80 150,50 C220,20 250,60 300,10" fill="none" stroke="#10B981" strokeWidth="3" className="stroke-emerald-base shadow-[0_0_10px_#10B981]" strokeLinecap="round" />
                    <defs>
                      <linearGradient id="gradient" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="0%" stopColor="#10B981" />
                        <stop offset="100%" stopColor="transparent" />
                      </linearGradient>
                    </defs>
                  </svg>
                </div>

                {/* Mock Transactions */}
                <div className="space-y-4 relative z-10 flex-1">
                  {[
                    { name: 'Зарплата', cat: 'Доход', val: '+ 120 000 ₽', icon: ArrowDownRight, color: 'text-emerald-base' },
                    { name: 'Продукты', cat: 'Питание', val: '- 4 500 ₽', icon: ArrowUpRight, color: 'text-rose-flash' },
                    { name: 'На отпуск', cat: 'Копилка', val: '- 10 000 ₽', icon: Target, color: 'text-blue-400' }
                  ].map((tx, i) => (
                    <div key={i} className="flex items-center justify-between p-3 rounded-xl bg-white/5 border border-white/5">
                      <div className="flex items-center gap-3">
                        <div className={`p-2 rounded-lg bg-white/5 ${tx.color}`}>
                          <tx.icon size={16} />
                        </div>
                        <div>
                          <div className="font-medium text-sm">{tx.name}</div>
                          <div className="text-slate-text text-xs">{tx.cat}</div>
                        </div>
                      </div>
                      <div className="font-mono text-sm">{tx.val}</div>
                    </div>
                  ))}
                </div>
              </div>

              {/* Decorative Blur elements behind mockup */}
              <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[120%] h-[120%] bg-main-bg -z-10 blur-[50px] mix-blend-multiply opacity-50" />
            </motion.div>
          </div>
        </section>

        {/* Features Section */}
        <section className="py-24 px-6 relative z-10">
          <div className="max-w-6xl mx-auto">
            <motion.div 
              initial="hidden"
              whileInView="visible"
              viewport={{ once: true, margin: "-100px" }}
              variants={staggerContainer}
              className="text-center max-w-2xl mx-auto mb-16"
            >
              <motion.h2 variants={fadeUpVariants} className="font-unbounded text-3xl md:text-5xl font-bold mb-6">
                Все что нужно для <br />
                <span className="text-emerald-base">финансового спокойствия</span>
              </motion.h2>
              <motion.p variants={fadeUpVariants} className="text-slate-text text-lg">
                Инструменты профессионального уровня в лаконичном и понятном интерфейсе.
              </motion.p>
            </motion.div>

            <motion.div 
              initial="hidden"
              whileInView="visible"
              viewport={{ once: true, margin: "-50px" }}
              variants={staggerContainer}
              className="grid sm:grid-cols-2 lg:grid-cols-3 gap-6"
            >
              <FeatureItem 
                icon={PieChart} 
                title="Бюджеты по месяцам" 
                description="Разделение доходов и расходов на периоды. Устанавливайте лимиты по категориям на каждый месяц отдельно."
              />
              <FeatureItem 
                icon={Target} 
                title="Умные Копилки" 
                description="Достигайте своих целей. Пополнения автоматически вычитаются из доступного бюджета месяца, чтобы вы не потратили лишнего."
              />
              <FeatureItem 
                icon={Activity} 
                title="Наглядная аналитика" 
                description="Подробные дешборды, круговые диаграммы структуры расходов и графики трендов за последние 6 месяцев."
              />
              <FeatureItem 
                icon={ShieldCheck} 
                title="Offline-first ядро" 
                description="Вносите траты даже без интернета. Мобильное и десктопное приложение автоматически синхронизирует данные при появлении сети."
              />
              <FeatureItem 
                icon={LayoutDashboard} 
                title="Zero UX Friction" 
                description="Drag-and-drop сортировка категорий, inline-редактирование сумм, адаптивный дизайн: sidebar на десктопе, bottom bar на смартфоне."
              />
              <FeatureItem 
                icon={Download} 
                title="Послушные бэкапы" 
                description="Ваши данные всегда в безопасности. Создавайте JSON-снапшоты и восстанавливайте их, сохраняя полную историю синхронизации."
              />
            </motion.div>
          </div>
        </section>

        {/* Download & CTA Section */}
        <section id="download" className="py-24 px-6 relative z-10 border-t border-white/5 bg-gradient-to-b from-transparent to-black/20">
          <div className="max-w-4xl mx-auto">
            <motion.div 
              initial="hidden"
              whileInView="visible"
              viewport={{ once: true }}
              variants={staggerContainer}
              className="glass-panel p-8 md:p-12 text-center"
            >
              <motion.h2 variants={fadeUpVariants} className="font-unbounded text-3xl md:text-5xl font-bold mb-4">
                Управляйте капиталом
              </motion.h2>
              <motion.p variants={fadeUpVariants} className="text-slate-text text-lg mb-12">
                Доступно на всех ваших устройствах с бесшовной синхронизацией.
              </motion.p>
              
              <motion.div variants={staggerContainer} className="grid sm:grid-cols-2 gap-4 text-left">
                <PlatformLink 
                  primary
                  icon={Monitor} 
                  title="Web-версия" 
                  desc="Полноценный SPA-клиент в браузере" 
                  url="http://217.114.8.82:3002/" 
                />
                <PlatformLink 
                  icon={Smartphone} 
                  title="Android" 
                  desc="Скачать APK-файл из RuStore" 
                  url="#" 
                />
                <PlatformLink 
                  icon={Monitor} 
                  title="macOS" 
                  desc="Compose Desktop (.dmg на GitHub)" 
                  url="#" 
                />
                <PlatformLink 
                  icon={Monitor} 
                  title="Windows" 
                  desc="Compose Desktop (.exe на GitHub)" 
                  url="#" 
                />
              </motion.div>
            </motion.div>
          </div>
        </section>
      </main>

      <footer className="py-10 px-6 mt-10 border-t border-white/10 text-center relative z-10">
        <p className="text-slate-text/70 text-sm font-mono">&copy; {new Date().getFullYear()} FinKeeper: Моя домашняя бухгалтерия. Все права защищены.</p>
      </footer>
    </div>
  )
}
