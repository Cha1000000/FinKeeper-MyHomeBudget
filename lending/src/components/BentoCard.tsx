import { type ReactNode } from 'react'
import { motion, type Variants } from 'framer-motion'

const fadeUpVariants: Variants = {
  hidden: { opacity: 0, y: 30 },
  visible: { opacity: 1, y: 0, transition: { duration: 0.8, ease: 'easeOut' } }
}

export const BentoCard = ({ children, className = "", span = "col-span-1" }: { children: ReactNode; className?: string; span?: string }) => (
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
