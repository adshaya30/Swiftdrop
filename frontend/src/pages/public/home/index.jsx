import React from 'react';
import { Link } from 'react-router-dom';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import { tokens } from '../../../theme/theme';
import heroCourierImg from '../../../assets/hero-courier.jpg';

export default function HomePage() {
  return (
    <Box
      sx={{
        minHeight: 'calc(100vh - 140px)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        p: { xs: 2, sm: 3, md: 6 },
      }}
    >
      <Card
        sx={{
          width: '100%',
          maxWidth: 1040,
          p: { xs: 3, sm: 5, md: 7 },
          borderRadius: tokens.radius.card,
          border: `1px solid ${tokens.palette.border}`,
          boxShadow: '0 8px 30px rgba(15, 23, 42, 0.05)',
          backgroundColor: tokens.palette.surface,
          display: 'grid',
          gridTemplateColumns: { xs: '1fr', md: '1.2fr 1fr' },
          alignItems: 'center',
          gap: { xs: 4, md: 6 },
        }}
      >
        {/* Left Hero Content */}
        <Box>
          <Typography
            variant="h2"
            sx={{
              fontWeight: 800,
              fontSize: { xs: '2.5rem', sm: '3.2rem', md: '3.75rem' },
              lineHeight: 1.15,
              color: tokens.palette.navy,
              mb: 1,
            }}
          >
            Fast Delivery.
            <Box component="span" sx={{ display: 'block', color: tokens.palette.primary }}>
              Everywhere.
            </Box>
          </Typography>

          <Typography
            variant="h6"
            sx={{
              fontWeight: 400,
              color: tokens.palette.mutedText,
              mb: 4,
              fontSize: { xs: '1rem', sm: '1.125rem' },
            }}
          >
            Reliable courier service across the island.
          </Typography>

          <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, alignItems: 'center' }}>
            <Button
              component={Link}
              to="/track"
              variant="contained"
              endIcon={<ArrowForwardIcon />}
              sx={{
                height: tokens.controlHeight.standard,
                backgroundColor: tokens.palette.primary,
                px: 3.5,
                fontWeight: 600,
                fontSize: '0.9375rem',
                borderRadius: tokens.radius.control,
                '&:hover': { backgroundColor: tokens.palette.primaryHover },
              }}
            >
              Track Your Parcel
            </Button>
            <Button
              component={Link}
              to="/login"
              variant="text"
              sx={{
                height: tokens.controlHeight.standard,
                color: tokens.palette.navy,
                fontWeight: 600,
                fontSize: '0.9375rem',
                textDecoration: 'underline',
                '&:hover': { backgroundColor: 'transparent', textDecoration: 'underline' },
              }}
            >
              Create Delivery
            </Button>
          </Box>
        </Box>

        {/* Right Hero Graphic Card */}
        <Box
          sx={{
            width: '100%',
            height: { xs: 260, sm: 340, md: 380 },
            borderRadius: tokens.radius.card,
            overflow: 'hidden',
            position: 'relative',
            backgroundColor: tokens.palette.softOrange,
            backgroundImage: `url(${heroCourierImg})`,
            backgroundSize: 'cover',
            backgroundPosition: 'center',
            boxShadow: '0 4px 20px rgba(0, 0, 0, 0.08)',
          }}
        />
      </Card>
    </Box>
  );
}
