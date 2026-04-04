import { motion, type Variants } from 'framer-motion'
import type { LucideIcon } from 'lucide-react'

export const StepCard = ({ number, title, desc, icon: Icon, variants }: { number: string; title: string; desc: string; icon: LucideIcon, variants: Variants }) => (
  <motion.div 
    variants={variants}
    className="relative group p-8 rounded-3xl glass-panel border-white/10 hover:border-emerald-base/30 transition-all duration-500 overflow-hidden"
  >
    <div className="absolute top-0 right-0 p-6 opacity-[0.03] group-hover:opacity-[0.08] transition-opacity duration-500">
      <span className="font-display font-black text-8xl md:text-9xl leading-none">{number}</span>
    </div>
    <div className="relative z-10">
      <div className="w-14 h-14 rounded-2xl bg-emerald-base/10 flex items-center justify-center mb-6 text-emerald-base shadow-lg shadow-emerald-base/5 border border-emerald-base/20 group-hover:scale-110 group-hover:rotate-3 transition-transform duration-500">
        <Icon size={28} strokeWidth={1.5} />
      </div>
      <h3 className="font-display font-bold text-2xl mb-4 text-white group-hover:text-emerald-base transition-colors duration-300">{title}</h3>
      <p className="text-slate-text leading-relaxed font-light">{desc}</p>
    </div>
    <div className="absolute bottom-0 left-0 h-1 w-0 bg-gradient-to-r from-emerald-400 to-emerald-600 group-hover:w-full transition-all duration-700 ease-out" />
  </motion.div>
);
