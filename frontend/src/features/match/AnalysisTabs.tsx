import { useTranslation } from "react-i18next";

export type AnalysisTab = "overview" | "turns" | "analysis" | "h2h";

export function AnalysisTabs({ value, onChange }: { value: AnalysisTab; onChange: (t: AnalysisTab) => void }) {
  const { t } = useTranslation();
  const tabs: AnalysisTab[] = ["overview", "turns", "analysis", "h2h"];
  return (
    <div role="tablist" className="mt-8 flex gap-1 overflow-x-auto border-b border-mortar">
      {tabs.map((x) => (
        <button
          key={x}
          role="tab"
          aria-selected={value === x}
          onClick={() => onChange(x)}
          className={`-mb-px shrink-0 border-b-2 px-4 py-3 text-sm font-semibold ${value === x ? "border-trafon text-trim" : "border-transparent text-trim-dim hover:text-trim"}`}
        >
          {t(`analysis.tabs.${x}`)}
        </button>
      ))}
    </div>
  );
}
