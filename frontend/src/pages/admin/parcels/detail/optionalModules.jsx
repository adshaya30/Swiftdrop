import React from 'react';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import { tokens } from '../../../../theme/theme';

/**
 * Optional composition (SRS 7.6, master 8.1). The parcel detail page composes pieces that other members publish:
 * api/tracking.js `getTimeline`, pages/public/components/ParcelTimeline.jsx and
 * pages/public/hooks/useTrackingStream.js (all #9). `import.meta.glob` resolves to an empty object while a file does
 * not exist, so the build never breaks; each piece swaps in automatically once its owner's pull request is merged.
 */
const first = (modules) => Object.values(modules)[0];

const trackingApi = first(import.meta.glob('/src/api/tracking.js', { eager: true }));
const timelineModule = first(import.meta.glob('/src/pages/public/components/ParcelTimeline.jsx', { eager: true }));
const streamModule = first(import.meta.glob('/src/pages/public/hooks/useTrackingStream.js', { eager: true }));

export const getTimeline = trackingApi?.getTimeline;
export const ParcelTimeline = timelineModule?.default ?? timelineModule?.ParcelTimeline;
export const hasTrackingStream = typeof streamModule?.useTrackingStream === 'function';
/** Safe to call unconditionally: the choice is made once at module load, so hook order never changes. */
export const useTrackingStream = hasTrackingStream ? streamModule.useTrackingStream : () => {};

/** Tile shown while a sibling piece is not available yet. */
export function PlaceholderTile({ title, message }) {
  return (
    <Box
      role="status"
      sx={{
        p: 2,
        borderRadius: tokens.radius.card,
        border: `1px dashed ${tokens.palette.border}`,
        backgroundColor: tokens.palette.mutedSurface,
      }}
    >
      <Typography sx={{ fontWeight: 600, color: tokens.palette.text }}>{title}</Typography>
      <Typography variant="body2" sx={{ color: tokens.palette.mutedText }}>{message}</Typography>
    </Box>
  );
}

/** A failure inside a composed section must never blank the rest of the page. */
export class SectionBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = { failed: false };
  }

  static getDerivedStateFromError() {
    return { failed: true };
  }

  render() {
    if (this.state.failed) {
      return <PlaceholderTile title={this.props.title} message="This section could not be displayed." />;
    }
    return this.props.children;
  }
}
