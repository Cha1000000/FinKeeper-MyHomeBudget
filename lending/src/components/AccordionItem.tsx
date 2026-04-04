import { useState } from 'react'
import { motion, AnimatePresence, type Variants } from 'framer-motion'
import { ChevronRight } from 'lucide-react'

export const AccordionItem = ({ question, answer, variants }: { question: string; answer: string, variants?: Variants }) => {
  const [isOpen, setIsOpen] = useState(false);

  return (
    <motion.div variants={variants} className="border-b border-white/10 last:border-0 group">
      <button 
        onClick={() => setIsOpen(!isOpen)}
        className="w-full flex items-center justify-between py-6 text-left focus:outline-none"
      >
        <span className={`font-display text-xl font-bold transition-colors duration-300 ${isOpen ? 'text-emerald-base' : 'text-white group-hover:text-emerald-base/80'}`}>
          {question}
        </span>
        <div className={`flex-shrink-0 ml-4 w-10 h-10 rounded-full flex items-center justify-center border transition-all duration-300 ${
          isOpen ? 'bg-emerald-base/10 border-emerald-base/30 text-emerald-base rotate-90' : 'bg-white/5 border-white/10 text-slate-text/50 group-hover:bg-white/10 group-hover:text-white'
        }`}>
          <ChevronRight size={20} />
        </div>
      </button>
      <AnimatePresence>
        {isOpen && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.3, ease: 'easeInOut' }}
            className="overflow-hidden"
          >
            <p className="pb-6 text-slate-text font-light leading-relaxed text-lg">
              {answer}
            </p>
          </motion.div>
        )}
      </AnimatePresence>
    </motion.div>
  );
};
