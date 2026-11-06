import { createTheme } from '@mui/material/styles';
import '@fontsource/inter/400.css';
import '@fontsource/inter/500.css';
import '@fontsource/inter/600.css';
import '@fontsource/inter/700.css';

/**
 * Design system tokens adhering to SWIFTDROP_MASTER_PROMPT Section 10.
 */
export const tokens = {
  palette: {
    primary: '#F97316',
    primaryHover: '#EA580C',
    primaryPressed: '#C2410C',
    navy: '#172033',
    navyLight: '#1E293B',
    softOrange: '#FFF7ED',
    pageBg: '#F8FAFC',
    surface: '#FFFFFF',
    text: '#0F172A',
    mutedText: '#64748B',
    border: '#E2E8F0',
    mutedSurface: '#F1F5F9',
    destructive: '#DC2626',
    destructiveHover: '#B91C1C',
    highlightYellow: '#FBBF24',
  },
  status: {
    CREATED: { bg: '#F1F5F9', text: '#64748B', label: 'Created' },
    PICKED_UP: { bg: '#EFF6FF', text: '#2563EB', label: 'Picked Up' },
    IN_TRANSIT: { bg: '#F5F3FF', text: '#7C3AED', label: 'In Transit' },
    OUT_FOR_DELIVERY: { bg: '#FFF7ED', text: '#EA580C', label: 'Out for Delivery' },
    DELIVERED: { bg: '#F0FDF4', text: '#16A34A', label: 'Delivered' },
    FAILED: { bg: '#FEF2F2', text: '#DC2626', label: 'Failed' },
    CANCELLED: { bg: '#F1F5F9', text: '#475569', label: 'Cancelled' },
  },
  radius: {
    control: '10px',
    card: '14px',
    chip: '16px',
  },
  controlHeight: {
    standard: '46px',
    courier: '52px',
  },
  focusRing: '0 0 0 3px rgba(249, 115, 22, 0.45)',
  transition: 'all 180ms cubic-bezier(0.4, 0, 0.2, 1)',
};

export const theme = createTheme({
  palette: {
    primary: {
      main: tokens.palette.primary,
      dark: tokens.palette.primaryHover,
      light: tokens.palette.softOrange,
      contrastText: '#FFFFFF',
    },
    secondary: {
      main: tokens.palette.navy,
      contrastText: '#FFFFFF',
    },
    error: {
      main: tokens.palette.destructive,
      dark: tokens.palette.destructiveHover,
    },
    background: {
      default: tokens.palette.pageBg,
      paper: tokens.palette.surface,
    },
    text: {
      primary: tokens.palette.text,
      secondary: tokens.palette.mutedText,
    },
    divider: tokens.palette.border,
  },
  typography: {
    fontFamily: '"Inter", -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
    h1: { fontWeight: 700, color: tokens.palette.text },
    h2: { fontWeight: 700, color: tokens.palette.text },
    h3: { fontWeight: 700, color: tokens.palette.text },
    h4: { fontWeight: 600, color: tokens.palette.text },
    h5: { fontWeight: 600, color: tokens.palette.text },
    h6: { fontWeight: 600, color: tokens.palette.text },
    button: { textTransform: 'none', fontWeight: 600 },
  },
  shape: {
    borderRadius: 10,
  },
  components: {
    MuiButton: {
      styleOverrides: {
        root: {
          height: tokens.controlHeight.standard,
          borderRadius: tokens.radius.control,
          transition: tokens.transition,
          boxShadow: 'none',
          '&:focus-visible': {
            boxShadow: tokens.focusRing,
          },
          '&:hover': {
            boxShadow: 'none',
          },
        },
        containedPrimary: {
          backgroundColor: tokens.palette.primary,
          '&:hover': {
            backgroundColor: tokens.palette.primaryHover,
          },
          '&:active': {
            backgroundColor: tokens.palette.primaryPressed,
          },
        },
      },
    },
    MuiOutlinedInput: {
      styleOverrides: {
        root: {
          borderRadius: tokens.radius.control,
          backgroundColor: tokens.palette.surface,
          transition: tokens.transition,
          '& .MuiOutlinedInput-notchedOutline': {
            borderColor: tokens.palette.border,
          },
          '&:hover .MuiOutlinedInput-notchedOutline': {
            borderColor: tokens.palette.mutedText,
          },
          '&.Mui-focused .MuiOutlinedInput-notchedOutline': {
            borderColor: tokens.palette.primary,
            borderWidth: '2px',
          },
        },
        input: {
          height: '18px',
          padding: '14px 16px',
        },
      },
    },
    MuiCard: {
      styleOverrides: {
        root: {
          borderRadius: tokens.radius.card,
          border: `1px solid ${tokens.palette.border}`,
          boxShadow: '0 4px 16px rgba(15, 23, 42, 0.04)',
        },
      },
    },
  },
});

export default theme;
