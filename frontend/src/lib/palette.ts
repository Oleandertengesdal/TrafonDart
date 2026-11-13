/**
 * The few colours that have to be raw values (chart libraries and SVG attributes can't read Tailwind classes).
 * Every value is a step on the scales in index.css, except the three chart series, which were validated with the
 * dataviz palette checker (lightness band, chroma, colour-blind separation, contrast) against `masonry` (#25221f):
 *
 *   rating line      #d95c48  facade red
 *   3-dart average   #b8892a  lamplight
 *   first-9 average  #4a90d9  winter-sky blue
 *
 * scripts/design.test.mjs allows hex values only in this file.
 */
export const palette = {
  // roles (mirror index.css)
  night: "#181614", // stone-950
  masonry: "#25221f", // stone-900
  masonry2: "#34302c", // stone-800
  mortar: "#47423c", // stone-700
  trimDim: "#bdb4a7", // stone-300
  trimFaint: "#a69d90", // stone-400
  trafon: "#ad3a2e", // brick-600
  heat: "#e47a66", // brick-400: the dartboard heatmap
} as const;

/** Chart series: distinct for every kind of colour vision. */
export const chart = {
  rating: "#d95c48",
  avg: "#b8892a",
  first9: "#4a90d9",
  grid: palette.mortar,
  axis: palette.trimFaint,
  surface: palette.masonry,
  cursor: palette.masonry2,
} as const;
