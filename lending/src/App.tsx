import React from 'react'
import { motion, useScroll, useTransform } from 'framer-motion'
import type { Variants } from 'framer-motion'
import type { LucideIcon } from 'lucide-react'
import { Wallet, PieChart, Activity, ArrowUpRight, ArrowDownRight, Target, Globe, Zap, Monitor, Smartphone, Download, ChevronRight, ShieldCheck, CheckCircle2, Star, Lock } from 'lucide-react'
import imgWebDashboard from './assets/web-dashboard-dark.webp'
import imgMobileDashboard from './assets/mobile-dashboard-1.webp'
import imgMobileDashboard2 from './assets/mobile-dashboard-2.webp'
import imgDesktopApp from './assets/desktop-dashboard.webp'
import imgMobileMonth from './assets/mobile-mont-dark.webp'

import { BentoCard } from './components/BentoCard'
import { FeatureIcon } from './components/FeatureIcon'
import { StepCard } from './components/StepCard'
import { AccordionItem } from './components/AccordionItem'

const ASSETS = {
  webDashboard: imgWebDashboard,
  mobileDashboard: imgMobileDashboard,
  mobileDashboard2: imgMobileDashboard2,
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


const PlatformLogo = ({ name }: { name: string }) => {
  const getSvgPath = (name: string) => {
    switch (name) {
      case 'android':
        return <path d="M17.523 15.3414c-.5511 0-.9993-.4486-.9993-.9997s.4483-.9993.9993-.9993c.5511 0 .9993.4483.9993.9993.0001.5511-.4482.9997-.9993.9997m-11.046 0c-.5511 0-.9993-.4486-.9993-.9997s.4483-.9993.9993-.9993c.5511 0 .9993.4483.9993.9993 0 .5511-.4483.9997-.9993.9997m11.4045-6.02 1.9973-3.4592a.416.416 0 0 0-.1521-.5676.416.416 0 0 0-.5676.1521l-2.0223 3.503C15.5902 8.2439 13.8533 7.8508 12 7.8508s-3.5902.3931-5.1367 1.0989L4.841 5.4467a.4159.4159 0 0 0-.5677-.1521.4157.4157 0 0 0-.1521.5676l1.9973 3.4592C2.6889 11.1867.3432 14.6589 0 18.761h24c-.3435-4.1021-2.6892-7.5743-6.1185-9.4396"/>;
      case 'windows':
        return <path d="M0,0H11.377V11.372H0ZM12.623,0H24V11.372H12.623ZM0,12.623H11.377V24H0Zm12.623,0H24V24H12.623"/>;
      case 'apple':
        return <path d="M12.152 6.896c-.948 0-2.415-1.078-3.96-1.04-2.04.027-3.91 1.183-4.961 3.014-2.117 3.675-.546 9.103 1.519 12.09 1.013 1.454 2.208 3.09 3.792 3.039 1.52-.065 2.09-.987 3.935-.987 1.831 0 2.35.987 3.96.948 1.637-.026 2.676-1.48 3.676-2.948 1.156-1.688 1.636-3.325 1.662-3.415-.039-.013-3.182-1.221-3.22-4.857-.026-3.04 2.48-4.494 2.597-4.559-1.429-2.09-3.623-2.324-4.39-2.376-2-.156-3.675 1.09-4.61 1.09zM15.53 3.83c.843-1.012 1.4-2.427 1.245-3.83-1.207.052-2.662.805-3.532 1.818-.78.896-1.454 2.338-1.273 3.714 1.338.104 2.715-.688 3.559-1.701"/>;
      case 'linux':
        return <path d="M12.504 0c-.155 0-.315.008-.48.021-4.226.333-3.105 4.807-3.17 6.298-.076 1.092-.3 1.953-1.05 3.02-.885 1.051-2.127 2.75-2.716 4.521-.278.832-.41 1.684-.287 2.489a.424.424 0 00-.11.135c-.26.268-.45.6-.663.839-.199.199-.485.267-.797.4-.313.136-.658.269-.864.68-.09.189-.136.394-.132.602 0 .199.027.4.055.536.058.399.116.728.04.97-.249.68-.28 1.145-.106 1.484.174.334.535.47.94.601.81.2 1.91.135 2.774.6.926.466 1.866.67 2.616.47.526-.116.97-.464 1.208-.946.587-.003 1.23-.269 2.26-.334.699-.058 1.574.267 2.577.2.025.134.063.198.114.333l.003.003c.391.778 1.113 1.132 1.884 1.071.771-.06 1.592-.536 2.257-1.306.631-.765 1.683-1.084 2.378-1.503.348-.199.629-.469.649-.853.023-.4-.2-.811-.714-1.376v-.097l-.003-.003c-.17-.2-.25-.535-.338-.926-.085-.401-.182-.786-.492-1.046h-.003c-.059-.054-.123-.067-.188-.135a.357.357 0 00-.19-.064c.431-1.278.264-2.55-.173-3.694-.533-1.41-1.465-2.638-2.175-3.483-.796-1.005-1.576-1.957-1.56-3.368.026-2.152.236-6.133-3.544-6.139zm.529 3.405h.013c.213 0 .396.062.584.198.19.135.33.332.438.533.105.259.158.459.166.724 0-.02.006-.04.006-.06v.105a.086.086 0 01-.004-.021l-.004-.024a1.807 1.807 0 01-.15.706.953.953 0 01-.213.335.71.71 0 00-.088-.042c-.104-.045-.198-.064-.284-.133a1.312 1.312 0 00-.22-.066c.05-.06.146-.133.183-.198.053-.128.082-.264.088-.402v-.02a1.21 1.21 0 00-.061-.4c-.045-.134-.101-.2-.183-.333-.084-.066-.167-.132-.267-.132h-.016c-.093 0-.176.03-.262.132a.8.8 0 00-.205.334 1.18 1.18 0 00-.09.4v.019c.002.089.008.179.02.267-.193-.067-.438-.135-.607-.202a1.635 1.635 0 01-.018-.2v-.02a1.772 1.772 0 01.15-.768c.082-.22.232-.406.43-.533a.985.985 0 01.594-.2zm-2.962.059h.036c.142 0 .27.048.399.135.146.129.264.288.344.465.09.199.14.4.153.667v.004c.007.134.006.2-.002.266v.08c-.03.007-.056.018-.083.024-.152.055-.274.135-.393.2.012-.09.013-.18.003-.267v-.015c-.012-.133-.04-.2-.082-.333a.613.613 0 00-.166-.267.248.248 0 00-.183-.064h-.021c-.071.006-.13.04-.186.132a.552.552 0 00-.12.27.944.944 0 00-.023.33v.015c.012.135.037.2.08.334.046.134.098.2.166.268.01.009.02.018.034.024-.07.057-.117.07-.176.136a.304.304 0 01-.131.068 2.62 2.62 0 01-.275-.402 1.772 1.772 0 01-.155-.667 1.759 1.759 0 01.08-.668 1.43 1.43 0 01.283-.535c.128-.133.26-.2.418-.2zm1.37 1.706c.332 0 .733.065 1.216.399.293.2.523.269 1.052.468h.003c.255.136.405.266.478.399v-.131a.571.571 0 01.016.47c-.123.31-.516.643-1.063.842v.002c-.268.135-.501.333-.775.465-.276.135-.588.292-1.012.267a1.139 1.139 0 01-.448-.067 3.566 3.566 0 01-.322-.198c-.195-.135-.363-.332-.612-.465v-.005h-.005c-.4-.246-.616-.512-.686-.71-.07-.268-.005-.47.193-.6.224-.135.38-.271.483-.336.104-.074.143-.102.176-.131h.002v-.003c.169-.202.436-.47.839-.601.139-.036.294-.065.466-.065zm2.8 2.142c.358 1.417 1.196 3.475 1.735 4.473.286.534.855 1.659 1.102 3.024.156-.005.33.018.513.064.646-1.671-.546-3.467-1.089-3.966-.22-.2-.232-.335-.123-.335.59.534 1.365 1.572 1.646 2.757.13.535.16 1.104.021 1.67.067.028.135.06.205.067 1.032.534 1.413.938 1.23 1.537v-.043c-.06-.003-.12 0-.18 0h-.016c.151-.467-.182-.825-1.065-1.224-.915-.4-1.646-.336-1.77.465-.008.043-.013.066-.018.135-.068.023-.139.053-.209.064-.43.268-.662.669-.793 1.187-.13.533-.17 1.156-.205 1.869v.003c-.02.334-.17.838-.319 1.35-1.5 1.072-3.58 1.538-5.348.334a2.645 2.645 0 00-.402-.533 1.45 1.45 0 00-.275-.333c.182 0 .338-.03.465-.067a.615.615 0 00.314-.334c.108-.267 0-.697-.345-1.163-.345-.467-.931-.995-1.788-1.521-.63-.4-.986-.87-1.15-1.396-.165-.534-.143-1.085-.015-1.645.245-1.07.873-2.11 1.274-2.763.107-.065.037.135-.408.974-.396.751-1.14 2.497-.122 3.854a8.123 8.123 0 01.647-2.876c.564-1.278 1.743-3.504 1.836-5.268.048.036.217.135.289.202.218.133.38.333.59.465.21.201.477.335.876.335.039.003.075.006.11.006.412 0 .73-.134.997-.268.29-.134.52-.334.74-.4h.005c.467-.135.835-.402 1.044-.7zm2.185 8.958c.037.6.343 1.245.882 1.377.588.134 1.434-.333 1.791-.765l.211-.01c.315-.007.577.01.847.268l.003.003c.208.199.305.53.391.876.085.4.154.78.409 1.066.486.527.645.906.636 1.14l.003-.007v.018l-.003-.012c-.015.262-.185.396-.498.595-.63.401-1.746.712-2.457 1.57-.618.737-1.37 1.14-2.036 1.191-.664.053-1.237-.2-1.574-.898l-.005-.003c-.21-.4-.12-1.025.056-1.69.176-.668.428-1.344.463-1.897.037-.714.076-1.335.195-1.814.12-.465.308-.797.641-.984l.045-.022zm-10.814.049h.01c.053 0 .105.005.157.014.376.055.706.333 1.023.752l.91 1.664.003.003c.243.533.754 1.064 1.189 1.637.434.598.77 1.131.729 1.57v.006c-.057.744-.48 1.148-1.125 1.294-.645.135-1.52.002-2.395-.464-.968-.536-2.118-.469-2.857-.602-.369-.066-.61-.2-.723-.4-.11-.2-.113-.602.123-1.23v-.004l.002-.003c.117-.334.03-.752-.027-1.118-.055-.401-.083-.71.043-.94.16-.334.396-.4.69-.533.294-.135.64-.202.915-.47h.002v-.002c.256-.268.445-.601.668-.838.19-.201.38-.336.663-.336zm7.159-9.074c-.435.201-.945.535-1.488.535-.542 0-.97-.267-1.28-.466-.154-.134-.28-.268-.373-.335-.164-.134-.144-.333-.074-.333.109.016.129.134.199.2.096.066.215.2.36.333.292.2.68.467 1.167.467.485 0 1.053-.267 1.398-.466.195-.135.445-.334.648-.467.156-.136.149-.267.279-.267.128.016.034.134-.147.332a8.097 8.097 0 01-.69.468zm-1.082-1.583V5.64c-.006-.02.013-.042.029-.05.074-.043.18-.027.26.004.063 0 .16.067.15.135-.006.049-.085.066-.135.066-.055 0-.092-.043-.141-.068-.052-.018-.146-.008-.163-.065zm-.551 0c-.02.058-.113.049-.166.066-.047.025-.086.068-.14.068-.05 0-.13-.02-.136-.068-.01-.066.088-.133.15-.133.08-.031.184-.047.259-.005.019.009.036.03.05v.02h.003z"/>;
      case 'googlechrome':
        return <path d="M12 0C8.21 0 4.831 1.757 2.632 4.501l3.953 6.848A5.454 5.454 0 0 1 12 6.545h10.691A12 12 0 0 0 12 0zM1.931 5.47A11.943 11.943 0 0 0 0 12c0 6.012 4.42 10.991 10.189 11.864l3.953-6.847a5.45 5.45 0 0 1-6.865-2.29zm13.342 2.166a5.446 5.446 0 0 1 1.45 7.09l.002.001h-.002l-5.344 9.257c.206.01.413.016.621.016 6.627 0 12-5.373 12-12 0-1.54-.29-3.011-.818-4.364zM12 16.364a4.364 4.364 0 1 1 0-8.728 4.364 4.364 0 0 1 0 8.728Z"/>;
      default:
        return null;
    }
  };

  return (
    <svg width="26" height="26" viewBox="0 0 24 24" className="text-slate-text/35 group-hover:text-emerald-base transition-colors duration-300" fill="currentColor">
      {getSvgPath(name)}
    </svg>
  );
};

const PlatformLink = ({ icon: Icon, title, desc, url, primary = false, badge }: { icon: LucideIcon; title: string; desc: string; url: string; primary?: boolean; badge?: string }) => (
  <motion.a 
    href={url}
    target="_blank"
    rel="noopener noreferrer"
    whileHover={{ scale: 1.02, y: -4 }}
    whileTap={{ scale: 0.98 }}
    className={`group relative flex items-center gap-5 p-4 sm:p-5 rounded-2xl border transition-all duration-500 h-full min-h-[44px] ${
      primary 
        ? 'bg-emerald-base/10 border-emerald-base/30 hover:bg-emerald-base/15 hover:border-emerald-base/50 shadow-[0_20px_40px_-15px_rgba(16,185,129,0.1)] hover:shadow-[0_30px_60px_-15px_rgba(16,185,129,0.25)]' 
        : 'bg-glass-bg border-glass-border hover:bg-white/5 hover:border-white/20 shadow-2xl shadow-black/20 hover:shadow-emerald-base/5'
    }`}
  >
    {badge && (
      <div className="absolute -top-3 right-4 px-2 py-1 rounded-full bg-emerald-base text-[9px] font-bold text-main-bg uppercase tracking-wider shadow-lg shadow-emerald-base/20 z-20">
        {badge}
      </div>
    )}
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


const PricingCard = ({ 
  title, 
  price, 
  features, 
  recommended = false, 
  icon: Icon,
  disabled = false,
  href 
}: { 
  title: string; 
  price: string; 
  features: string[]; 
  recommended?: boolean; 
  icon: LucideIcon;
  disabled?: boolean;
  href?: string;
}) => (
  <motion.div
    variants={fadeUpVariants}
    whileHover={{ y: -10 }}
    className={`relative p-8 rounded-3xl border transition-all duration-500 h-full flex flex-col ${
      recommended
        ? 'bg-emerald-base/5 border-emerald-base/30 shadow-[0_30px_60px_-15px_rgba(16,185,129,0.15)]'
        : 'bg-glass-bg border-glass-border shadow-2xl'
    } ${disabled ? 'opacity-70' : ''}`}
  >
    {recommended && (
      <div className="absolute -top-4 left-1/2 -translate-x-1/2 px-4 py-1.5 rounded-full bg-amber-400 text-[9px] font-black text-main-bg uppercase tracking-[0.2em] shadow-xl z-20">
        В разработке
      </div>
    )}
    {disabled && (
      <div className="absolute top-4 right-4 px-3 py-1.5 rounded-full bg-white/10 text-[9px] font-bold text-slate-text uppercase tracking-wider border border-white/10 z-20">
        Скоро
      </div>
    )}
    <div className="mb-8">
      <div className={`w-12 h-12 rounded-xl flex items-center justify-center mb-6 ${recommended ? 'bg-emerald-base text-main-bg shadow-[0_0_20px_rgba(16,185,129,0.4)]' : 'bg-white/5 text-white border border-white/10'}`}>
        <Icon size={24} strokeWidth={2} />
      </div>
      <h3 className="font-display font-bold text-2xl mb-2 text-white">{title}</h3>
      <div className="flex items-baseline gap-1">
        <span className="text-4xl font-display font-black text-white">{price}</span>
        {price !== 'Бесплатно' && <span className="text-slate-text/60 text-sm">/ месяц</span>}
      </div>
      {disabled && (
        <p className="text-xs text-slate-text/60 mt-3 font-light leading-relaxed">
          Весь функционал доступен бесплатно в базовой версии
        </p>
      )}
    </div>
    <ul className="space-y-4 mb-10 flex-1">
      {features.map((feature, i) => (
        <li key={i} className={`flex items-start gap-3 text-sm leading-relaxed font-light ${
          disabled ? 'text-slate-text/50' : 'text-slate-text/80'
        }`}>
          <CheckCircle2 size={18} className={`${disabled ? 'text-slate-text/30' : 'text-emerald-base'} flex-shrink-0 mt-0.5`} />
          {feature}
        </li>
      ))}
    </ul>
    {href ? (
      <a
        href={href}
        target="_blank"
        rel="noopener noreferrer"
        className={`w-full py-4 min-h-[44px] rounded-2xl font-display font-bold transition-all duration-300 flex items-center justify-center gap-2 ${
          recommended
            ? 'bg-emerald-base text-main-bg hover:bg-emerald-400 shadow-lg shadow-emerald-base/20'
            : 'bg-white/5 text-white border border-white/10 hover:bg-white/10'
        }`}
      >
        {recommended ? 'Попробовать PRO' : 'Начать бесплатно'}
        {!recommended && <ArrowUpRight size={18} />}
      </a>
    ) : (
      <button 
        disabled={disabled}
        className={`w-full py-4 rounded-2xl font-display font-bold transition-all duration-300 ${
          recommended
            ? 'bg-slate-600 text-slate-400 cursor-not-allowed'
            : 'bg-white/5 text-white border border-white/10 hover:bg-white/10'
        }`}
      >
        {recommended ? 'В разработке' : 'Попробовать PRO'}
      </button>
    )}
  </motion.div>
);


const ReviewCard = ({ name, role, text, avatar, rating }: { name: string; role: string; text: string; avatar: string; rating: number }) => (
  <motion.div
    variants={fadeUpVariants}
    className="flex-shrink-0 w-[280px] sm:w-[320px] md:w-[360px] p-6 rounded-3xl glass-panel border-white/10 hover:border-emerald-base/30 transition-all duration-500 group"
  >
    <div className="flex items-center gap-1 mb-4">
      {[...Array(5)].map((_, i) => (
        <Star
          key={i}
          size={14}
          className={`${i < rating ? 'text-amber-400 fill-amber-400' : 'text-slate-text/30'}`}
        />
      ))}
    </div>
    <p className="text-slate-text/90 text-sm leading-relaxed font-light mb-6 line-clamp-4">
      "{text}"
    </p>
    <div className="flex items-center gap-3">
      <div className="w-10 h-10 rounded-full bg-gradient-to-br from-emerald-400 to-emerald-600 flex items-center justify-center text-main-bg font-display font-bold text-sm">
        {avatar}
      </div>
      <div>
        <div className="text-white font-display font-semibold text-sm">{name}</div>
        <div className="text-slate-text/50 text-xs font-light">{role}</div>
      </div>
    </div>
  </motion.div>
);

const ReviewsCarousel = () => {
  const scrollRef = React.useRef<HTMLDivElement>(null);
  const [showLeftArrow, setShowLeftArrow] = React.useState(false);
  const [showRightArrow, setShowRightArrow] = React.useState(true);

  const scroll = (direction: 'left' | 'right') => {
    if (scrollRef.current) {
      const scrollAmount = 400;
      const newScrollLeft = direction === 'left' 
        ? scrollRef.current.scrollLeft - scrollAmount 
        : scrollRef.current.scrollLeft + scrollAmount;
      
      scrollRef.current.scrollTo({
        left: newScrollLeft,
        behavior: 'smooth'
      });
    }
  };

  const handleScroll = () => {
    if (scrollRef.current) {
      setShowLeftArrow(scrollRef.current.scrollLeft > 0);
      setShowRightArrow(
        scrollRef.current.scrollLeft < scrollRef.current.scrollWidth - scrollRef.current.clientWidth - 10
      );
    }
  };

  React.useEffect(() => {
    const el = scrollRef.current;
    if (el) {
      el.addEventListener('scroll', handleScroll);
      return () => el.removeEventListener('scroll', handleScroll);
    }
  }, []);

  return (
    <div className="relative group/carousel">
      {/* Left Arrow */}
      <motion.button
        initial={{ opacity: 0.25 }}
        animate={{ opacity: showLeftArrow ? 0.25 : 0 }}
        whileHover={{ opacity: 0.7 }}
        transition={{ duration: 0.2 }}
        onClick={() => scroll('left')}
        className="absolute left-0 top-[90px] z-30 w-12 h-12 rounded-full bg-emerald-base border-2 border-white/20 shadow-2xl flex items-center justify-center cursor-pointer transition-all duration-300 hover:scale-110 -ml-6 md:-ml-12"
        aria-label="Прокрутить влево"
      >
        <ChevronRight size={24} className="text-white rotate-180" strokeWidth={2.5} />
      </motion.button>

      {/* Reviews Scroll Container */}
      <div
        ref={scrollRef}
        className="flex gap-6 overflow-x-auto pb-8 -mx-6 px-6 scrollbar-hide scroll-smooth"
        onScroll={handleScroll}
      >
        <ReviewCard
          name="Александр М."
          role="Предприниматель, Москва"
          text="Наконец-то перестал вести учёт в Excel! FinKeeper24 намного удобнее, а синхронизация между телефоном и компьютером работает мгновенно."
          avatar="АМ"
          rating={5}
        />
        <ReviewCard
          name="Елена К."
          role="Фрилансер, Санкт-Петербург"
          text="Пользуюсь уже 4 месяца. Очень нравится функция копилок — помогает откладывать на отпуск! Интерфейс красивый и понятный, разобралась за 10 минут."
          avatar="ЕК"
          rating={5}
        />
        <ReviewCard
          name="Дмитрий Б."
          role="Разработчик, Ростов-на-Дону"
          text="Как разработчик, ценю качественный софт. Здесь видно, что сделано с душой. Никакой рекламы, всё работает быстро. Desktop-версия для macOS — просто огонь!"
          avatar="ДБ"
          rating={5}
        />
        <ReviewCard
          name="Павел Г."
          role="Менеджер, Новосибирск"
          text="Хорошее приложение, пользуюсь уже полгода. Не хватает экспорта данных в Excel — иногда нужно добыть отчёт. Но в целом всё работает чётко, интерфейс удобный."
          avatar="ПГ"
          rating={4}
        />
        <ReviewCard
          name="Ольга С."
          role="Бухгалтер, Екатеринбург"
          text="Работаю бухгалтером, поэтому порядок в финансах для меня важен. А FinKeeper24 теперь помогает мне вести мои личные бюджеты. Копилки — гениальная функция!"
          avatar="ОС"
          rating={5}
        />
        <ReviewCard
          name="Михаил П."
          role="Студент, Казань"
          text="Бесплатной версии мне более чем достаточно! Веду учёт стипендии и подработок. Стал гораздо осознаннее тратить деньги. Рекомендую всем друзьям."
          avatar="МП"
          rating={5}
        />
        <ReviewCard
          name="Анна и Игорь"
          role="Молодая семья, Краснодар"
          text="Вдвоём ведём бюджет семьи на общем аккаунте. Очень удобно, что оба видим все операции в реальном времени, каждый на своём телефоне. Спасибо разработчикам!"
          avatar="АИ"
          rating={5}
        />
        <ReviewCard
          name="Ирина В."
          role="Маркетолог, Самара"
          text="В целом приложение нравится: быстрое, красивое. Жаль нет пуш-уведомлений о плановых тратах, если выхожу за лимиты. Хотелось бы увидеть такую функцию."
          avatar="ИВ"
          rating={4}
        />
        <ReviewCard
          name="Наталья Р."
          role="Декрет, Нижний Новгород"
          text="С ребёнком не всегда есть время записывать траты. Здесь можно добавить расход за 5 секунд! Офлайн-режим спасает, когда интернет пропадает."
          avatar="НР"
          rating={5}
        />
        <ReviewCard
          name="Сергей Л."
          role="IT-специалист, Москва"
          text="Перепробовал много приложений: Дзен-мани, CoinKeeper, 1Money. FinKeeper24 оказался проще, но есть ещё что было бы не плохо доработать."
          avatar="СЛ"
          rating={4}
        />
        <ReviewCard
          name="Татьяна Б."
          role="Пенсионерка, Воронеж"
          text="Мне 62 года, но приложение освоила легко. Крупные цифры, понятные кнопки. Теперь вижу все свои расходы по категориям. Внуки помогли разобраться."
          avatar="ТБ"
          rating={5}
        />
        <ReviewCard
          name="Владимир К."
          role="Водитель такси, Ростов-на-Дону"
          text="Работаю в такси, доходы нерегулярные. FinKeeper24 помогает планировать бюджет на месяц вперёд. По графикам смотрю, где можно сэкономить."
          avatar="ВК"
          rating={5}
        />
      </div>

      {/* Right Arrow */}
      <motion.button
        initial={{ opacity: 0.25 }}
        animate={{ opacity: showRightArrow ? 0.25 : 0 }}
        whileHover={{ opacity: 0.7 }}
        transition={{ duration: 0.2 }}
        onClick={() => scroll('right')}
        className="absolute right-0 top-[90px] z-30 w-12 h-12 rounded-full bg-emerald-base border-2 border-white/20 shadow-2xl flex items-center justify-center cursor-pointer transition-all duration-300 hover:scale-110 -mr-6 md:-mr-12"
        aria-label="Прокрутить вправо"
      >
        <ChevronRight size={24} className="text-white" strokeWidth={2.5} />
      </motion.button>
    </div>
  );
};

export default function App() {
  const { scrollY } = useScroll();
  const y1 = useTransform(scrollY, [0, 1000], [0, 200]);
  const y2 = useTransform(scrollY, [0, 1000], [0, -150]);
  const y3 = useTransform(scrollY, [0, 1000], [0, 150]);

  const [scrolled, setScrolled] = React.useState(false);
  React.useEffect(() => {
    const handler = () => setScrolled(window.scrollY > 50);
    window.addEventListener('scroll', handler, { passive: true });
    return () => window.removeEventListener('scroll', handler);
  }, []);

  return (
    <div className="min-h-screen relative font-body bg-main-bg text-white overflow-x-hidden">
      {/* Background Orbs */}
      <div className="fixed inset-0 pointer-events-none z-0">
        <motion.div style={{ y: y1 }} className="absolute -top-[20%] -left-[10%] w-[50vw] h-[50vw] rounded-full bg-emerald-glow/20 blur-[120px] mix-blend-screen opacity-50 animate-pulse-slow" />
        <motion.div style={{ y: y2 }} className="absolute top-[40%] -right-[20%] w-[60vw] h-[60vw] rounded-full bg-blue-900/20 blur-[150px] mix-blend-screen opacity-40 animate-pulse-slow" />
        <div className="absolute -bottom-[20%] left-[20%] w-[40vw] h-[40vw] rounded-full bg-emerald-base/10 blur-[100px] mix-blend-screen opacity-30" />
      </div>

      {/* Navigation */}
      <nav className={`fixed top-0 inset-x-0 z-50 px-6 py-4 transition-all duration-500 ${scrolled ? 'bg-[#0A0F1E]/80 backdrop-blur-xl border-b border-white/5 shadow-2xl shadow-black/30' : ''}`}>
        <div className="max-w-6xl mx-auto flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <img src="/purse.svg" alt="FinKeeper24" className="w-9 h-9 object-contain" />
            <span className="font-display font-semibold text-xl tracking-tight">FinKeeper24: Моя домашняя бухгалтерия</span>
          </div>
          <a href="#download" className="hidden sm:inline-flex items-center justify-center px-5 py-2.5 min-h-[44px] rounded-full bg-white/10 hover:bg-white/20 border border-white/5 backdrop-blur-md transition-all font-display font-medium text-sm">
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
                v2.2.2 • PRO SYSTEM
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
                style={{ rotateX: 10, y: y3 }}
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

        {/* Problem/Pain Section */}
        <section className="py-24 md:py-32 px-6 relative z-10 overflow-hidden">
          {/* Background */}
          <div className="absolute inset-0 bg-gradient-to-b from-main-bg via-rose-900/5 to-main-bg -z-10" />
          <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[60%] h-[60%] bg-rose-500/5 blur-[150px] rounded-full -z-10" />
          
          <div className="max-w-6xl mx-auto">
            <motion.div 
              initial="hidden"
              whileInView="visible"
              viewport={{ once: true, margin: "-100px" }}
              variants={staggerContainer}
              className="text-center"
            >

              {/* Main text */}
              <motion.p variants={fadeUpVariants} className="font-display text-2xl md:text-3xl lg:text-4xl font-light leading-relaxed text-white/80 max-w-3xl mx-auto mb-6">
                Знакомо это чувство, когда <span className="text-white font-semibold">к концу месяца вы уже не помните</span>, куда ушли деньги?
              </motion.p>
              
              <motion.p variants={fadeUpVariants} className="font-display text-xl md:text-2xl font-light leading-relaxed text-slate-text/70 max-w-2xl mx-auto mb-10">
                Заначка исчезает незаметно, а мечты о путешествии или новом ноутбуке снова откладываются.
              </motion.p>

              {/* Pain points */}
              <motion.div variants={fadeUpVariants} className="flex flex-wrap items-center justify-center gap-3 md:gap-4 mb-12">
                {[
                  { icon: '📉', text: 'Траты без контроля' },
                  { icon: '💸', text: 'Деньги улетают' },
                  { icon: '🎯', text: 'Цели откладываются' },
                ].map((item, i) => (
                  <div key={i} className="px-5 py-3 rounded-full bg-white/5 border border-white/10 backdrop-blur-sm">
                    <span className="text-sm font-light text-slate-text/80">{item.icon} {item.text}</span>
                  </div>
                ))}
              </motion.div>

              {/* CTA */}
              <motion.a 
                variants={fadeUpVariants}
                href="https://app.finkeeper24.ru"
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center gap-3 px-10 py-6 rounded-2xl bg-emerald-base text-main-bg font-display font-bold text-lg hover:bg-emerald-400 transition-all shadow-[0_20px_40px_-10px_rgba(16,185,129,0.3)] hover:-translate-y-1"
              >
                Пора взять свои финансы под контроль! <ArrowUpRight size={20} />
              </motion.a>
            </motion.div>
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

        {/* How It Works Section */}
        <section className="py-24 md:py-32 px-6 relative z-10 bg-black/20">
          <div className="max-w-7xl mx-auto">
            <motion.div 
              initial="hidden"
              whileInView="visible"
              viewport={{ once: true }}
              variants={staggerContainer}
              className="text-center mb-16 md:mb-20"
            >
              <motion.h2 variants={fadeUpVariants} className="font-display text-4xl md:text-5xl lg:text-[64px] font-bold mb-6 tracking-tight text-white leading-[1.05]">
                Три шага к <span className="text-emerald-base text-glow">финансовой свободе</span>
              </motion.h2>
              <motion.p variants={fadeUpVariants} className="text-slate-text text-xl max-w-2xl mx-auto font-light leading-relaxed">
                Мы сделали процесс управления деньгами максимально простым и естественным. Начните прямо сейчас.
              </motion.p>
            </motion.div>

            <motion.div 
              initial="hidden"
              whileInView="visible"
              viewport={{ once: true }}
              variants={staggerContainer}
              className="grid md:grid-cols-3 gap-8 md:gap-8"
            >
              <StepCard 
                number="01"
                icon={Download}
                title="Установка"
                desc="Скачайте приложение на любое устройство. Или воспользуйтесь Web-версией. Регистрация займет меньше минуты — мы ценим ваше время."
                variants={fadeUpVariants}
              />
              <StepCard 
                number="02"
                icon={Wallet}
                title="Первые записи"
                desc="Задайте названия своих обычных категорий доходов и расходов. Добавляйте доходы и расходы в один клик. Приложение само распределит их по категориям для наглядности."
                variants={fadeUpVariants}
              />
              <StepCard 
                number="03"
                icon={Activity}
                title="Аналитика"
                desc="Следите за динамикой через стильные графики. Приложение подскажет, где можно сэкономить и как быстрее достичь целей."
                variants={fadeUpVariants}
              />
            </motion.div>
          </div>
        </section>

        {/* Pricing Section */}
        <section className="py-24 md:py-32 px-6 relative z-10 overflow-hidden">
          <div className="max-w-7xl mx-auto">
            <motion.div 
              initial="hidden"
              whileInView="visible"
              viewport={{ once: true }}
              variants={staggerContainer}
              className="text-center mb-16 md:mb-20"
            >
              <motion.h2 variants={fadeUpVariants} className="font-display text-4xl md:text-5xl lg:text-[64px] font-bold mb-6 tracking-tight text-white leading-[1.05]">
                Выберите свой <span className="text-emerald-base text-glow">уровень контроля</span>
              </motion.h2>
              <motion.p variants={fadeUpVariants} className="text-slate-text text-xl max-w-2xl mx-auto font-light leading-relaxed">
                Начните бесплатно. PRO-функции в разработке — весь функционал уже доступен в базовой версии.
              </motion.p>
            </motion.div>

            <div className="grid md:grid-cols-2 gap-8 max-w-5xl mx-auto items-stretch">
              <PricingCard
                title="Базовый"
                price="Бесплатно"
                icon={Wallet}
                href="https://app.finkeeper24.ru"
                features={[
                  "Учет доходов и расходов",
                  "Лимиты по категориям",
                  "Умные копилки",
                  "Локальное хранение данных",
                  "Синхронизация"
                ]}
              />
              <PricingCard
                recommended
                disabled
                title="PRO SYSTEM"
                price="199 ₽"
                icon={Star}
                features={[
                  "Неограниченно устройств",
                  "Семейный доступ",
                  "Создание резервных копий и восстановление",
                  "Продвинутая аналитика",
                  "Приоритетная поддержка"
                ]}
              />
            </div>
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
                <motion.h2 variants={fadeUpVariants} className="font-display text-4xl sm:text-5xl md:text-5xl lg:text-6xl xl:text-[64px] font-bold mb-6 md:mb-8 leading-[1] text-center lg:text-left text-white tracking-tight">
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
                    badge="Рекомендуем"
                  />
                  <div className="grid sm:grid-cols-2 gap-4 items-stretch">
                    <PlatformLink 
                      icon={Smartphone} 
                      title="Android" 
                      desc="• RuStore • APK • Play Store" 
                      url="https://www.rustore.ru/catalog/app/ru.homebudget.finkeeper" 
                    />
                    <PlatformLink 
                      icon={Monitor} 
                      title="Desktop" 
                      desc="• Windows • macOS • Linux" 
                      url="/downloads.html" 
                    />
                  </div>
                </motion.div>

                {/* Trust Signals */}
                <motion.div
                  variants={fadeUpVariants}
                  className="mt-4 grid grid-cols-3 gap-4"
                >
                  <div className="flex flex-col items-center text-center p-4 rounded-2xl bg-emerald-base/5 border border-emerald-base/20 backdrop-blur-sm group hover:bg-emerald-base/10 transition-all duration-300">
                    <ShieldCheck size={28} className="text-emerald-base mb-3 group-hover:scale-110 transition-transform duration-300" />
                    <span className="text-base font-display font-bold text-white">100% Приватность</span>
                    <span className="text-xs text-slate-text/70 mt-1 font-light">Ваши данные под защитой</span>
                  </div>
                  <div className="flex flex-col items-center text-center p-4 rounded-2xl bg-amber-400/5 border border-amber-400/20 backdrop-blur-sm group hover:bg-amber-400/10 transition-all duration-300">
                    <Star size={28} className="text-amber-400 mb-3 group-hover:scale-110 transition-transform duration-300" />
                    <span className="text-base font-display font-bold text-white">Рейтинг 4.7+</span>
                    <span className="text-xs text-slate-text/70 mt-1 font-light">Оценка пользователей</span>
                  </div>
                  <div className="flex flex-col items-center text-center p-4 rounded-2xl bg-emerald-base/5 border border-emerald-base/20 backdrop-blur-sm group hover:bg-emerald-base/10 transition-all duration-300">
                    <CheckCircle2 size={28} className="text-emerald-base mb-3 group-hover:scale-110 transition-transform duration-300" />
                    <span className="text-base font-display font-bold text-white">Без рекламы</span>
                    <span className="text-xs text-slate-text/70 mt-1 font-light">Ничего лишнего</span>
                  </div>

                  {/* Platform Logos */}
                  <div className="col-span-3 flex flex-col items-center mt-2 pt-4 border-t border-white/5">
                    <motion.span 
                      initial={{ opacity: 0 }}
                      whileInView={{ opacity: 1 }}
                      transition={{ delay: 0.3 }}
                      className="text-[10px] uppercase tracking-[0.2em] text-slate-text/40 mb-4"
                    >
                      Доступно на
                    </motion.span>
                    <div className="flex items-center justify-center gap-5 md:gap-8">
                      {/* Android */}
                      <motion.a 
                        href="https://www.rustore.ru/catalog/app/ru.homebudget.finkeeper"
                        whileHover={{ scale: 1.15 }}
                        className="group"
                        aria-label="Android"
                      >
                        <PlatformLogo name="android" />
                      </motion.a>

                      {/* Windows */}
                      <motion.a 
                        href="/downloads.html"
                        whileHover={{ scale: 1.15 }}
                        className="group"
                        aria-label="Windows"
                      >
                        <PlatformLogo name="windows" />
                      </motion.a>

                      {/* Apple/macOS */}
                      <motion.a 
                        href="/downloads.html"
                        whileHover={{ scale: 1.15 }}
                        className="group"
                        aria-label="macOS"
                      >
                        <PlatformLogo name="apple" />
                      </motion.a>

                      {/* Linux */}
                      <motion.a 
                        href="/downloads.html"
                        whileHover={{ scale: 1.15 }}
                        className="group"
                        aria-label="Linux"
                      >
                        <PlatformLogo name="linux" />
                      </motion.a>

                      {/* Google Chrome (Web) */}
                      <motion.a 
                        href="https://app.finkeeper24.ru"
                        whileHover={{ scale: 1.15 }}
                        className="group"
                        aria-label="Web"
                      >
                        <PlatformLogo name="googlechrome" />
                      </motion.a>
                    </div>
                  </div>
                </motion.div>
              </motion.div>

              <motion.div
                initial={{ opacity: 0, scale: 0.95 }}
                whileInView={{ opacity: 1, scale: 1 }}
                viewport={{ once: true }}
                transition={{ duration: 1 }}
                className="relative w-full max-w-[1100px] mx-auto mt-8 md:mt-24"
                style={{ minHeight: '700px' }}
              >
                {/* Background Glow */}
                <div className="absolute inset-0 bg-emerald-base/5 blur-[120px] rounded-full opacity-50 -z-10" />

                {/* Device Stack - Professional Composition */}
                <div className="relative w-full">
                  {/* Web Dashboard - Full Width Background */}
                  <motion.div
                    initial={{ opacity: 0, y: 40 }}
                    whileInView={{ opacity: 1, y: 0 }}
                    transition={{ duration: 1, delay: 0.1 }}
                    className="w-full glass-panel border-white/15 shadow-2xl rounded-3xl overflow-hidden z-0"
                  >
                    <img src={ASSETS.webDashboard} alt="Web Dashboard" loading="lazy" className="w-full h-auto object-cover" />
                  </motion.div>

                  {/* Mobile Main - Large, Front Left (2x Size) */}
                  <motion.div
                    initial={{ opacity: 0, y: 80, rotate: -3 }}
                    whileInView={{ opacity: 1, y: 0, rotate: -2 }}
                    transition={{ duration: 0.9, delay: 0.3 }}
                    className="absolute -bottom-24 md:-bottom-50 left-[2%] md:left-[-10%] w-[50%] md:w-[45%] max-w-[420px] z-20"
                  >
                    <div className="glass-panel border-white/30 shadow-2xl rounded-[48px] overflow-hidden p-3 md:p-4">
                      <div className="w-full aspect-[9/19] rounded-[24px] overflow-hidden relative bg-black border border-white/5">
                        <img src={ASSETS.mobileDashboard} alt="Mobile Dashboard" loading="lazy" className="w-full h-full object-cover" />
</div>
                    </div>
                  </motion.div>

                  {/* Mobile Month View - Top Right, Medium Size */}
                  <motion.div
                    initial={{ opacity: 0, y: -60, rotate: 5 }}
                    whileInView={{ opacity: 1, y: 0, rotate: 3 }}
                    transition={{ duration: 0.9, delay: 0.5 }}
                    className="absolute -top-16 md:-top-50 right-[5%] md:right-[5%] w-[45%] md:w-[40%] max-w-[340px] z-10"
                  >
                    <div className="glass-panel border-white/25 shadow-2xl rounded-[40px] overflow-hidden p-3 md:p-4">
                      <div className="w-full aspect-[9/19] rounded-[24px] overflow-hidden relative bg-black border border-white/5">
                        <img src={ASSETS.mobileMonth} alt="Month View" className="w-full h-full object-cover" />
                      </div>
                    </div>
                  </motion.div>

                  {/* Mobile Dashboard 2 - Bottom Right, Smaller (2x Size) */}
                  <motion.div
                    initial={{ opacity: 0, y: 60, rotate: -5 }}
                    whileInView={{ opacity: 1, y: 0, rotate: 4 }}
                    transition={{ duration: 0.9, delay: 0.7 }}
                    className="hidden md:block absolute bottom-12 md:top-87 right-[0%] md:right-[10%] w-[45%] md:w-[38%] max-w-[280px] z-10"
                  >
                    <div className="glass-panel border-white/20 shadow-xl rounded-[36px] overflow-hidden p-2 md:p-3">
                      <div className="w-full aspect-[9/19] rounded-[24px] overflow-hidden relative bg-black border border-white/5">
                        <img src={ASSETS.mobileDashboard2} alt="Mobile View 2" className="w-full h-full object-cover" />
                      </div>
                    </div>
                  </motion.div>
                </div>
              </motion.div>
            </div>
          </div>
        </section>

        {/* Reviews Section */}
        <section className="py-20 md:py-28 px-6 relative z-10 overflow-hidden">
          <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[60%] h-[60%] bg-emerald-base/5 blur-[150px] rounded-full -z-10" />
          
          <div className="max-w-7xl mx-auto">
            <motion.div
              initial="hidden"
              whileInView="visible"
              viewport={{ once: true }}
              variants={staggerContainer}
              className="text-center mb-16"
            >
              <motion.h2 variants={fadeUpVariants} className="font-display text-4xl md:text-5xl lg:text-[64px] font-bold mb-6 tracking-tight text-white leading-[1.05]">
                Что говорят <span className="text-emerald-base text-glow">пользователи</span>
              </motion.h2>
              <motion.p variants={fadeUpVariants} className="text-slate-text text-xl max-w-2xl mx-auto font-light leading-relaxed">
                Отзывы тех, кто уже взяли свои финансы под контроль с FinKeeper24
              </motion.p>
            </motion.div>

            <motion.div
              initial="hidden"
              whileInView="visible"
              viewport={{ once: true }}
              variants={staggerContainer}
            >
              <ReviewsCarousel />
            </motion.div>
          </div>
        </section>

        {/* FAQ Section */}
        <section className="py-24 md:py-32 px-6 relative z-10 bg-black/20">
          <div className="max-w-4xl mx-auto">
            <motion.div 
              initial="hidden"
              whileInView="visible"
              viewport={{ once: true }}
              variants={staggerContainer}
              className="text-center mb-16 md:mb-20"
            >
              <motion.h2 variants={fadeUpVariants} className="font-display text-4xl md:text-5xl lg:text-[64px] font-bold mb-6 tracking-tight text-white leading-[1.05]">
                Частые <span className="text-emerald-base text-glow">вопросы</span>
              </motion.h2>
            </motion.div>

            <motion.div 
              initial="hidden"
              whileInView="visible"
              viewport={{ once: true }}
              variants={staggerContainer}
              className="glass-panel p-8 md:p-12 border-white/5"
            >
              <AccordionItem
                question="Где хранятся мои данные?"
                answer="Все данные хранятся непосредственно на ваших устройствах — в памяти приложения. А так же в зашифрованном виде на сервере приложения для обеспечения синхронизации данных между вашими устройствами и возможности создавать резервные копии. Никто не имеет доступа к вашей финансовой информации."
              />
              <AccordionItem
                question="Это безопасно?"
                answer="Абсолютно. FinKeeper24 не требует доступа к вашим банковским аккаунтам. Мы не собираем персональные данные и не передаем информацию третьим лицам."
              />
              <AccordionItem
                question="Как работает синхронизация?"
                answer="Синхронизация происходит мгновенно при наличии интернета. Если вы внесли данные офлайн, они обновятся на других устройствах при первом выходе в сеть."
              />
              <AccordionItem
                question="Можно ли восстановить данные при ошибке?"
                answer="Да, на сервере автоматически создаются резервные копии вашей бухгалтерии. А так же вы можете создавать их самостоятельно в любой момент, когда вам это понадобится. Если вы случайно удалили запись или допустили ошибку при заполнении сумм доходов/расходов/копилок, вы можете восстановить данные из последней сохранённой копии. Это защищает от случайных потерь при заполнении."
              />
            </motion.div>
          </div>
        </section>

        {/* Final CTA Section */}
        <section className="py-24 md:py-40 px-6 relative z-10 overflow-hidden">
          <div className="max-w-5xl mx-auto text-center relative">
            <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[120%] h-[120%] bg-emerald-base/10 blur-[150px] rounded-full -z-10" />
            
            <motion.div
              initial={{ opacity: 0, scale: 0.9 }}
              whileInView={{ opacity: 1, scale: 1 }}
              viewport={{ once: true }}
              transition={{ duration: 1 }}
            >
              <h2 className="font-display text-4xl md:text-6xl lg:text-7xl font-black text-white mb-10 tracking-tight leading-tight">
                Готовы взять финансы <br /> 
                <span className="text-transparent bg-clip-text bg-gradient-to-r from-emerald-300 to-emerald-500 text-glow">под полный контроль?</span>
              </h2>
              <div className="flex flex-col sm:flex-row items-center justify-center gap-6">
                <a href="https://app.finkeeper24.ru" target="_blank" rel="noopener noreferrer" className="w-full sm:w-auto px-12 py-6 rounded-2xl bg-emerald-base text-main-bg font-display font-bold text-lg hover:bg-emerald-400 transition-all shadow-[0_20px_40px_-10px_rgba(16,185,129,0.4)] hover:-translate-y-1 flex items-center justify-center gap-3">
                  Начать использование <ArrowUpRight size={24} />
                </a>
              </div>
            </motion.div>
          </div>
        </section>

      </main>

      <footer className="pt-16 pb-10 px-6 border-t border-white/10 relative z-10">
        <div className="max-w-5xl mx-auto">
          {/* Top row: logo + nav + contact */}
          <div className="flex flex-col md:flex-row items-center md:items-start justify-between gap-10 mb-12">
            {/* Brand */}
            <div className="flex flex-col items-center md:items-start gap-3">
              <div className="flex items-center gap-2.5">
                <img src="/purse.svg" alt="FinKeeper24" className="w-9 h-9 object-contain" />
                <span className="font-display font-semibold text-lg tracking-tight text-white">FinKeeper24</span>
              </div>
              <p className="text-slate-text/50 text-sm font-light max-w-[220px] text-center md:text-left">Домашняя бухгалтерия для всех ваших устройств</p>
            </div>

            {/* Nav links */}
            <div className="flex flex-col items-center md:items-start gap-3">
              <span className="text-[10px] uppercase tracking-[0.2em] text-slate-text/40 font-mono">Навигация</span>
              <div className="flex flex-wrap justify-center md:justify-start gap-x-6 gap-y-2 text-sm text-slate-text/60">
                <a href="#" className="hover:text-emerald-base transition-colors duration-300">Возможности</a>
                <a href="#download" className="hover:text-emerald-base transition-colors duration-300">Скачать</a>
                <a href="https://app.finkeeper24.ru" target="_blank" rel="noopener noreferrer" className="hover:text-emerald-base transition-colors duration-300">Войти</a>
              </div>
            </div>

            {/* Contact */}
            <div className="flex flex-col items-center md:items-start gap-3">
              <span className="text-[10px] uppercase tracking-[0.2em] text-slate-text/40 font-mono">Обратная связь</span>
              <a
                href="mailto:finkeeper24@yandex.ru"
                className="flex items-center gap-2 text-sm text-slate-text/60 hover:text-emerald-base transition-colors duration-300 group"
              >
                <div className="w-7 h-7 rounded-lg bg-emerald-base/10 border border-emerald-base/20 flex items-center justify-center group-hover:bg-emerald-base/20 transition-colors duration-300">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-emerald-base">
                    <rect width="20" height="16" x="2" y="4" rx="2"/><path d="m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7"/>
                  </svg>
                </div>
                finkeeper24@yandex.ru
              </a>
            </div>
          </div>

          {/* Divider */}
          <div className="border-t border-white/5 pt-8">
            {/* Legal links */}
            <div className="flex flex-wrap items-center justify-center gap-4 text-xs text-slate-text/40 mb-6">
              <p className="font-mono">&copy; {new Date().getFullYear()} FinKeeper24. Все права защищены.</p>
              <span className="hidden sm:inline text-slate-text/20">•</span>
              <a href="/privacy.html" className="hover:text-emerald-base transition-colors duration-300">Политика конфиденциальности</a>
              <span className="hidden sm:inline text-slate-text/20">•</span>
              <a href="/terms.html" className="hover:text-emerald-base transition-colors duration-300">Пользовательское соглашение</a>
            </div>
            <div className="text-[10px] leading-relaxed text-slate-text/25 font-light max-w-2xl mx-auto text-center">
              FinKeeper24 — это удобное кроссплатформенное приложение (онлайн сервис) для ведения домашней бухгалтерии, учета личных финансов и семейного бюджета. Контролируйте свои доходы и расходы, создавайте копилки на мечту, планируйте бюджет по категориям и следите за своей финансовой статистикой онлайн на любых устройствах: web, Android, Windows, macOS, Linux. Современная альтернатива таблицам Excel. Учет финансов еще никогда не был таким простым и безопасным.
            </div>
          </div>
        </div>
      </footer>
    </div>
  )
}
