import type { LucideIcon } from 'lucide-react'

export const FeatureIcon = ({ icon: Icon, color = "text-emerald-base" }: { icon: LucideIcon; color?: string }) => (
  <div className={`w-12 h-12 rounded-xl bg-white/5 border border-white/10 flex items-center justify-center mb-6 ${color} shadow-lg transition-transform duration-500 group-hover:scale-110 group-hover:rotate-3`}>
    <Icon size={24} strokeWidth={1.5} />
  </div>
);
