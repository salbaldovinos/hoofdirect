# FRD-015: Marketing Website

**Version:** 1.0  
**Last Updated:** January 2026  
**PRD Reference:** PRD-015-marketing-site.md  
**Priority:** P1  
**Phase:** 5 - Monetization

---

## 1. Overview

### 1.1 Purpose

Create a professional marketing website at hoofdirect.com to convert visitors into app downloads and paying subscribers. The site serves as the primary landing page for organic search traffic, paid campaigns, and direct marketing.

### 1.2 Scope

This FRD covers:
- Next.js web application structure
- Homepage content and layout
- Pricing page with tier comparison
- SEO optimization
- Legal pages (Privacy Policy, Terms of Service)
- Analytics integration

**Note:** This FRD documents a web application separate from the Android app. It uses Next.js 14 with App Router, deployed on Vercel.

### 1.3 Dependencies

| Dependency | Type | FRD Reference |
|------------|------|---------------|
| Domain (hoofdirect.com) | External | - |
| Vercel account | External | - |
| Play Store listing | External | FRD-021 |

---

## 2. Technical Stack

### 2.1 Framework and Deployment

| Component | Technology | Version |
|-----------|------------|---------|
| Framework | Next.js | 14.x (App Router) |
| Styling | Tailwind CSS | 3.x |
| Deployment | Vercel | - |
| Analytics | Google Analytics 4 | - |
| CMS | None (static content) | - |

### 2.2 Project Structure

```
hoofdirect-web/
├── app/
│   ├── layout.tsx           # Root layout with metadata
│   ├── page.tsx             # Homepage
│   ├── pricing/
│   │   └── page.tsx         # Pricing page
│   ├── privacy/
│   │   └── page.tsx         # Privacy Policy
│   ├── terms/
│   │   └── page.tsx         # Terms of Service
│   └── globals.css          # Global styles
├── components/
│   ├── Header.tsx           # Navigation header
│   ├── Footer.tsx           # Site footer
│   ├── Hero.tsx             # Hero section
│   ├── Features.tsx         # Feature grid
│   ├── PricingTable.tsx     # Pricing comparison
│   ├── FAQ.tsx              # FAQ accordion
│   └── CTAButton.tsx        # Play Store CTA
├── public/
│   ├── images/
│   │   ├── hero-screenshot.png
│   │   ├── app-icon.png
│   │   └── feature-icons/
│   ├── sitemap.xml
│   └── robots.txt
└── next.config.js
```

---

## 3. Homepage Sections

### 3.1 Hero Section

**Route:** `/` (above the fold)

Content:
- Headline: "Route-Optimized Scheduling for Professional Farriers"
- Subheadline: "Save 5+ hours every week with intelligent route planning, offline scheduling, and simple invoicing"
- Primary CTA: [Download on Google Play] → Play Store listing URL
- Secondary CTA: [See Pricing] → `/pricing`
- Hero image: App screenshot showing route map view

```tsx
// components/Hero.tsx
interface HeroProps {
  playStoreUrl: string;
}

const Hero: React.FC<HeroProps> = ({ playStoreUrl }) => (
  <section className="min-h-[80vh] flex flex-col items-center justify-center px-4 py-16">
    <h1 className="text-4xl md:text-6xl font-bold text-center max-w-4xl">
      Route-Optimized Scheduling for Professional Farriers
    </h1>
    <p className="text-xl text-gray-600 text-center mt-6 max-w-2xl">
      Save 5+ hours every week with intelligent route planning, 
      offline scheduling, and simple invoicing
    </p>
    <div className="flex flex-col sm:flex-row gap-4 mt-8">
      <a href={playStoreUrl} className="btn-primary">
        <PlayStoreIcon className="w-6 h-6 mr-2" />
        Download on Google Play
      </a>
      <a href="/pricing" className="btn-secondary">
        See Pricing
      </a>
    </div>
    <div className="mt-12">
      <Image
        src="/images/hero-screenshot.png"
        alt="Hoof Direct route optimization"
        width={800}
        height={600}
        priority
      />
    </div>
  </section>
);
```

### 3.2 Problem Statement Section

Content highlighting pain points:
- "Manual route planning wastes hours"
- "Paper scheduling leads to missed appointments"
- "Tracking mileage is a hassle"
- "Sending invoices takes too long"

Each pain point paired with the Hoof Direct solution.

### 3.3 Features Grid

Six feature cards in a 2×3 or 3×2 responsive grid:

| Feature | Icon | Description |
|---------|------|-------------|
| Route Optimization | 🗺️ | "Automatically reorder stops to minimize drive time. Save 20%+ on fuel." |
| Calendar Sync | 🔄 | "Two-way sync with Google Calendar. Your schedule everywhere." |
| Offline Mode | 📴 | "Full functionality without cell service. Sync when back in range." |
| Invoicing | 💰 | "Generate professional invoices in seconds. Track payments easily." |
| Mileage Tracking | 📊 | "Log trips automatically for tax deductions. Export IRS-ready reports." |
| Client Management | 👥 | "Keep all client and horse records organized in one place." |

```tsx
// components/Features.tsx
const features = [
  {
    icon: "🗺️",
    title: "Route Optimization",
    description: "Automatically reorder stops to minimize drive time. Save 20%+ on fuel.",
    highlight: true  // Hero feature gets visual emphasis
  },
  // ... other features
];

const Features: React.FC = () => (
  <section className="py-16 px-4 bg-gray-50">
    <h2 className="text-3xl font-bold text-center">Everything you need to run your business</h2>
    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8 mt-12 max-w-6xl mx-auto">
      {features.map((feature) => (
        <FeatureCard key={feature.title} {...feature} />
      ))}
    </div>
  </section>
);
```

### 3.4 Pricing Preview

Abbreviated pricing table showing all four tiers:

| Tier | Price | Key Features |
|------|-------|--------------|
| Free | $0/mo | 10 clients, No routes |
| Solo Farrier | $29/mo | Unlimited clients, 8 stops/day |
| Growing Practice | $79/mo | 15 stops/day, 2 users |
| Multi-Farrier | $149/mo | Unlimited routes, 5 users |

CTA: [View Full Pricing] → `/pricing`

### 3.5 FAQ Section

Accordion-style FAQ with common questions:

| Question | Answer |
|----------|--------|
| Does it work offline? | Yes! Hoof Direct works completely offline. Create appointments, view schedules, and manage clients without cell service. Everything syncs automatically when you're back online. |
| How does route optimization work? | When you have multiple appointments for a day, tap "Optimize Route" and we'll reorder your stops to minimize total drive time. Uses the same technology as UPS and FedEx. |
| Can I try before I buy? | Absolutely. The Free tier lets you manage up to 10 clients with full scheduling features. Upgrade anytime when you're ready for route optimization. |
| Do you offer annual billing? | Yes, save 20% with annual billing. All paid plans offer monthly and annual options. |
| Can I import my existing clients? | Not yet, but we're working on CSV import. For now, adding clients is quick and easy. |
| What if I need help? | Email us at support@hoofdirect.com. We respond within 24 hours on business days. |

```tsx
// components/FAQ.tsx
const faqs = [
  {
    question: "Does it work offline?",
    answer: "Yes! Hoof Direct works completely offline..."
  },
  // ... other FAQs
];

const FAQ: React.FC = () => {
  const [openIndex, setOpenIndex] = useState<number | null>(null);
  
  return (
    <section className="py-16 px-4">
      <h2 className="text-3xl font-bold text-center">Frequently Asked Questions</h2>
      <div className="max-w-3xl mx-auto mt-12">
        {faqs.map((faq, index) => (
          <FAQItem
            key={index}
            question={faq.question}
            answer={faq.answer}
            isOpen={openIndex === index}
            onToggle={() => setOpenIndex(openIndex === index ? null : index)}
          />
        ))}
      </div>
    </section>
  );
};
```

### 3.6 Final CTA Section

Full-width section with:
- Headline: "Ready to save hours every week?"
- Primary CTA: [Download on Google Play]
- Trust indicators: "Free to start • No credit card required • Works offline"

### 3.7 Footer

Footer content:

**Column 1 - Product:**
- Features
- Pricing
- Download

**Column 2 - Company:**
- About (placeholder)
- Contact

**Column 3 - Legal:**
- Privacy Policy
- Terms of Service

**Bottom row:**
- © 2025 Hoof Direct. All rights reserved.
- Social links (placeholder)

---

## 4. Pricing Page

### 4.1 Route and Layout

**Route:** `/pricing`

Full pricing comparison with feature breakdown per tier.

### 4.2 Pricing Table Structure

```tsx
// components/PricingTable.tsx
interface PricingTier {
  name: string;
  monthlyPrice: number;
  annualPrice: number;
  priceId: {
    monthly: string;
    annual: string;
  };
  features: {
    clients: string;
    horses: string;
    photos: string;
    routeStops: string;
    smsPerMonth: string;
    users: string;
    calendarSync: boolean;
    invoicing: boolean;
    mileageTracking: boolean;
  };
  highlighted?: boolean;
  ctaText: string;
}

const tiers: PricingTier[] = [
  {
    name: "Free",
    monthlyPrice: 0,
    annualPrice: 0,
    priceId: { monthly: "", annual: "" },
    features: {
      clients: "10",
      horses: "30",
      photos: "50",
      routeStops: "0 (no optimization)",
      smsPerMonth: "0",
      users: "1",
      calendarSync: true,
      invoicing: true,
      mileageTracking: true
    },
    ctaText: "Start Free"
  },
  {
    name: "Solo Farrier",
    monthlyPrice: 29,
    annualPrice: 278,
    priceId: { monthly: "price_solo_monthly", annual: "price_solo_annual" },
    features: {
      clients: "Unlimited",
      horses: "Unlimited",
      photos: "Unlimited",
      routeStops: "8 per day",
      smsPerMonth: "50",
      users: "1",
      calendarSync: true,
      invoicing: true,
      mileageTracking: true
    },
    highlighted: true,
    ctaText: "Get Solo"
  },
  {
    name: "Growing Practice",
    monthlyPrice: 79,
    annualPrice: 758,
    priceId: { monthly: "price_growing_monthly", annual: "price_growing_annual" },
    features: {
      clients: "Unlimited",
      horses: "Unlimited",
      photos: "Unlimited",
      routeStops: "15 per day",
      smsPerMonth: "200",
      users: "2",
      calendarSync: true,
      invoicing: true,
      mileageTracking: true
    },
    ctaText: "Get Growing"
  },
  {
    name: "Multi-Farrier",
    monthlyPrice: 149,
    annualPrice: 1430,
    priceId: { monthly: "price_multi_monthly", annual: "price_multi_annual" },
    features: {
      clients: "Unlimited",
      horses: "Unlimited",
      photos: "Unlimited",
      routeStops: "Unlimited",
      smsPerMonth: "500",
      users: "5",
      calendarSync: true,
      invoicing: true,
      mileageTracking: true
    },
    ctaText: "Get Multi"
  }
];
```

### 4.3 Feature Comparison Table

Full feature comparison table below the pricing cards:

| Feature | Free | Solo | Growing | Multi |
|---------|------|------|---------|-------|
| **Clients** | 10 | Unlimited | Unlimited | Unlimited |
| **Horses** | 30 | Unlimited | Unlimited | Unlimited |
| **Photos** | 50 | Unlimited | Unlimited | Unlimited |
| **Route Optimization** | ❌ | 8 stops/day | 15 stops/day | Unlimited |
| **SMS Reminders** | ❌ | 50/month | 200/month | 500/month |
| **Team Members** | 1 | 1 | 2 | 5 |
| **Calendar Sync** | ✅ | ✅ | ✅ | ✅ |
| **Invoicing** | ✅ | ✅ | ✅ | ✅ |
| **Mileage Tracking** | ✅ | ✅ | ✅ | ✅ |
| **Offline Mode** | ✅ | ✅ | ✅ | ✅ |
| **Priority Support** | ❌ | ❌ | ✅ | ✅ |

### 4.4 Billing Toggle

Toggle between monthly and annual pricing:

```tsx
const PricingToggle: React.FC<{
  isAnnual: boolean;
  onToggle: () => void;
}> = ({ isAnnual, onToggle }) => (
  <div className="flex items-center justify-center gap-4 mb-8">
    <span className={!isAnnual ? "font-semibold" : "text-gray-500"}>Monthly</span>
    <button
      onClick={onToggle}
      className="relative w-14 h-8 bg-primary rounded-full"
    >
      <span 
        className={`absolute w-6 h-6 bg-white rounded-full transition-transform ${
          isAnnual ? "translate-x-7" : "translate-x-1"
        }`}
      />
    </button>
    <span className={isAnnual ? "font-semibold" : "text-gray-500"}>
      Annual <span className="text-green-600">(Save 20%)</span>
    </span>
  </div>
);
```

---

## 5. Legal Pages

### 5.1 Privacy Policy

**Route:** `/privacy`

Required sections:
- Information We Collect
- How We Use Your Information
- Data Storage and Security
- Third-Party Services (Google Maps, Stripe)
- Your Rights (CCPA, GDPR basics)
- Contact Information
- Last Updated Date

Content served as static MDX or plain HTML.

### 5.2 Terms of Service

**Route:** `/terms`

Required sections:
- Acceptance of Terms
- Description of Service
- User Accounts and Responsibilities
- Payment Terms
- Intellectual Property
- Disclaimer of Warranties
- Limitation of Liability
- Termination
- Governing Law
- Changes to Terms
- Contact Information

---

## 6. SEO Configuration

### 6.1 Metadata

```tsx
// app/layout.tsx
export const metadata: Metadata = {
  title: {
    default: "Hoof Direct - Route-Optimized Farrier Scheduling App",
    template: "%s | Hoof Direct"
  },
  description: "Save hours every week with intelligent route planning, offline scheduling, and simple invoicing. The only farrier app with route optimization.",
  keywords: [
    "farrier app",
    "farrier scheduling",
    "farrier software",
    "horse shoeing app",
    "route optimization",
    "farrier business",
    "equine scheduling"
  ],
  authors: [{ name: "Hoof Direct" }],
  creator: "Hoof Direct",
  publisher: "Hoof Direct",
  openGraph: {
    type: "website",
    locale: "en_US",
    url: "https://hoofdirect.com",
    siteName: "Hoof Direct",
    title: "Hoof Direct - Route-Optimized Farrier Scheduling App",
    description: "Save hours every week with intelligent route planning for professional farriers.",
    images: [
      {
        url: "https://hoofdirect.com/images/og-image.png",
        width: 1200,
        height: 630,
        alt: "Hoof Direct App"
      }
    ]
  },
  twitter: {
    card: "summary_large_image",
    title: "Hoof Direct - Farrier Scheduling App",
    description: "Route-optimized scheduling for professional farriers.",
    images: ["https://hoofdirect.com/images/twitter-image.png"]
  },
  robots: {
    index: true,
    follow: true,
    googleBot: {
      index: true,
      follow: true,
      "max-video-preview": -1,
      "max-image-preview": "large",
      "max-snippet": -1
    }
  }
};
```

### 6.2 Structured Data

```tsx
// app/layout.tsx - JSON-LD for SoftwareApplication
const structuredData = {
  "@context": "https://schema.org",
  "@type": "SoftwareApplication",
  "name": "Hoof Direct",
  "applicationCategory": "BusinessApplication",
  "operatingSystem": "Android",
  "offers": {
    "@type": "Offer",
    "price": "0",
    "priceCurrency": "USD"
  },
  "aggregateRating": {
    "@type": "AggregateRating",
    "ratingValue": "4.8",  // Update with actual rating
    "ratingCount": "100"   // Update with actual count
  },
  "description": "Route-optimized scheduling app for professional farriers"
};
```

### 6.3 Sitemap

```xml
<!-- public/sitemap.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
  <url>
    <loc>https://hoofdirect.com</loc>
    <lastmod>2025-01-01</lastmod>
    <changefreq>weekly</changefreq>
    <priority>1.0</priority>
  </url>
  <url>
    <loc>https://hoofdirect.com/pricing</loc>
    <lastmod>2025-01-01</lastmod>
    <changefreq>monthly</changefreq>
    <priority>0.9</priority>
  </url>
  <url>
    <loc>https://hoofdirect.com/privacy</loc>
    <lastmod>2025-01-01</lastmod>
    <changefreq>yearly</changefreq>
    <priority>0.3</priority>
  </url>
  <url>
    <loc>https://hoofdirect.com/terms</loc>
    <lastmod>2025-01-01</lastmod>
    <changefreq>yearly</changefreq>
    <priority>0.3</priority>
  </url>
</urlset>
```

### 6.4 Robots.txt

```
# public/robots.txt
User-agent: *
Allow: /
Disallow: /api/

Sitemap: https://hoofdirect.com/sitemap.xml
```

---

## 7. Performance Requirements

### 7.1 Core Web Vitals Targets

| Metric | Target | Measurement |
|--------|--------|-------------|
| Largest Contentful Paint (LCP) | < 2.5s | Lighthouse |
| First Input Delay (FID) | < 100ms | Lighthouse |
| Cumulative Layout Shift (CLS) | < 0.1 | Lighthouse |
| Time to First Byte (TTFB) | < 600ms | WebPageTest |

### 7.2 Lighthouse Score Targets

| Category | Target |
|----------|--------|
| Performance | > 90 |
| Accessibility | > 95 |
| Best Practices | > 95 |
| SEO | > 95 |

### 7.3 Optimization Techniques

1. **Image Optimization**
   - Use Next.js `<Image>` component for automatic optimization
   - Serve WebP format with fallbacks
   - Lazy load below-fold images
   - Specify width/height to prevent layout shift

2. **Font Loading**
   - Use `next/font` for Google Fonts
   - Preload critical fonts
   - Use `font-display: swap`

3. **Code Splitting**
   - Automatic per-page code splitting via Next.js
   - Dynamic imports for FAQ accordion, pricing toggle

4. **Caching**
   - Static pages cached at edge (Vercel)
   - Images cached with long TTL

---

## 8. Analytics Integration

### 8.1 Google Analytics 4

```tsx
// app/layout.tsx
import { GoogleAnalytics } from '@next/third-parties/google';

export default function RootLayout({ children }) {
  return (
    <html lang="en">
      <body>
        {children}
        <GoogleAnalytics gaId="G-XXXXXXXXXX" />
      </body>
    </html>
  );
}
```

### 8.2 Key Events to Track

| Event | Trigger | Parameters |
|-------|---------|------------|
| `page_view` | Auto | page_path, page_title |
| `play_store_click` | Play Store CTA clicked | source (hero, footer, pricing) |
| `pricing_viewed` | Pricing page opened | - |
| `pricing_toggle` | Monthly/annual toggled | billing_period |
| `faq_expanded` | FAQ question opened | question_id |
| `scroll_depth` | 25%, 50%, 75%, 100% | depth |

```tsx
// lib/analytics.ts
export const trackEvent = (eventName: string, params?: Record<string, any>) => {
  if (typeof window !== 'undefined' && window.gtag) {
    window.gtag('event', eventName, params);
  }
};

// Usage
<a 
  href={playStoreUrl}
  onClick={() => trackEvent('play_store_click', { source: 'hero' })}
>
  Download on Google Play
</a>
```

---

## 9. Responsive Design

### 9.1 Breakpoints

| Breakpoint | Width | Target Device |
|------------|-------|---------------|
| sm | 640px | Large phones |
| md | 768px | Tablets |
| lg | 1024px | Small laptops |
| xl | 1280px | Desktops |
| 2xl | 1536px | Large displays |

### 9.2 Mobile Considerations

- Hamburger menu for navigation on mobile
- Stacked pricing cards on mobile (horizontal scroll optional)
- Touch-friendly tap targets (min 48×48px)
- Reduced image sizes for mobile

### 9.3 Accessibility

| Requirement | Implementation |
|-------------|----------------|
| Color contrast | WCAG AA (4.5:1 minimum) |
| Focus indicators | Visible focus rings on interactive elements |
| Alt text | All images have descriptive alt text |
| Semantic HTML | Proper heading hierarchy (h1 → h2 → h3) |
| Keyboard navigation | All interactive elements reachable via keyboard |
| Screen reader | ARIA labels where needed |

---

## 10. Deployment

### 10.1 Vercel Configuration

```json
// vercel.json
{
  "framework": "nextjs",
  "regions": ["iad1"],
  "headers": [
    {
      "source": "/(.*)",
      "headers": [
        {
          "key": "X-Frame-Options",
          "value": "DENY"
        },
        {
          "key": "X-Content-Type-Options",
          "value": "nosniff"
        },
        {
          "key": "Referrer-Policy",
          "value": "strict-origin-when-cross-origin"
        }
      ]
    }
  ],
  "redirects": [
    {
      "source": "/download",
      "destination": "https://play.google.com/store/apps/details?id=com.hoofdirect.app",
      "permanent": false
    }
  ]
}
```

### 10.2 Environment Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `NEXT_PUBLIC_GA_ID` | Google Analytics ID | G-XXXXXXXXXX |
| `NEXT_PUBLIC_PLAY_STORE_URL` | Play Store listing URL | https://play.google.com/... |
| `NEXT_PUBLIC_SUPPORT_EMAIL` | Support email | support@hoofdirect.com |

### 10.3 CI/CD

- Push to `main` branch triggers production deployment
- Preview deployments for pull requests
- Lighthouse CI check on each PR

---

## 11. Acceptance Criteria

### 11.1 Homepage

| ID | Criteria | Verification |
|----|----------|--------------|
| AC-015-01 | Page loads in < 2 seconds on 3G | Lighthouse throttled |
| AC-015-02 | Hero section displays correctly on mobile (375px) | Manual test |
| AC-015-03 | Play Store link opens correct app listing | Manual test |
| AC-015-04 | All 6 feature cards display with correct content | Manual test |
| AC-015-05 | FAQ accordion expands/collapses correctly | Manual test |
| AC-015-06 | Footer links navigate to correct pages | Manual test |

### 11.2 Pricing Page

| ID | Criteria | Verification |
|----|----------|--------------|
| AC-015-07 | All 4 pricing tiers display correct prices | Manual test |
| AC-015-08 | Annual toggle shows 20% discount | Manual test |
| AC-015-09 | Feature comparison table accurate vs PRD-017 | Manual test |
| AC-015-10 | "Get Started" buttons link to Play Store | Manual test |

### 11.3 Legal Pages

| ID | Criteria | Verification |
|----|----------|--------------|
| AC-015-11 | Privacy Policy accessible at /privacy | Manual test |
| AC-015-12 | Terms of Service accessible at /terms | Manual test |
| AC-015-13 | Legal pages load without JavaScript | curl test |

### 11.4 SEO

| ID | Criteria | Verification |
|----|----------|--------------|
| AC-015-14 | Meta title contains "farrier" and "scheduling" | View source |
| AC-015-15 | Sitemap.xml accessible and valid | XML validator |
| AC-015-16 | robots.txt allows crawling | Manual test |
| AC-015-17 | Structured data validates without errors | Schema validator |
| AC-015-18 | Open Graph image displays in social shares | Facebook debugger |

### 11.5 Performance

| ID | Criteria | Verification |
|----|----------|--------------|
| AC-015-19 | Lighthouse Performance score > 90 | Lighthouse CI |
| AC-015-20 | Lighthouse Accessibility score > 95 | Lighthouse CI |
| AC-015-21 | No layout shift on image load (CLS < 0.1) | Lighthouse |
| AC-015-22 | Mobile responsive at all breakpoints | Chrome DevTools |

### 11.6 Analytics

| ID | Criteria | Verification |
|----|----------|--------------|
| AC-015-23 | Page views tracked in GA4 | GA4 real-time |
| AC-015-24 | Play Store clicks tracked with source | GA4 events |
| AC-015-25 | Pricing toggle events tracked | GA4 events |

---

## 12. Future Enhancements

### 12.1 Testimonials Section (Post-Launch)

Once beta users provide feedback:
- Add testimonials carousel on homepage
- Include farrier names, photos, locations (with permission)
- Star ratings from Play Store reviews

### 12.2 Blog (Phase 2)

Content marketing for SEO:
- "How to optimize your farrier routes"
- "Tax deductions for farriers"
- "Scheduling tips for busy farriers"

### 12.3 Help Center (Phase 2)

Knowledge base for self-service support:
- Getting started guides
- Feature documentation
- Video tutorials

---

## 13. File References

### 13.1 Web Project Structure

```
hoofdirect-web/
├── app/
│   ├── layout.tsx
│   ├── page.tsx
│   ├── pricing/page.tsx
│   ├── privacy/page.tsx
│   ├── terms/page.tsx
│   └── globals.css
├── components/
│   ├── Header.tsx
│   ├── Footer.tsx
│   ├── Hero.tsx
│   ├── Features.tsx
│   ├── FeatureCard.tsx
│   ├── PricingTable.tsx
│   ├── PricingCard.tsx
│   ├── PricingToggle.tsx
│   ├── FAQ.tsx
│   ├── FAQItem.tsx
│   ├── CTASection.tsx
│   └── CTAButton.tsx
├── lib/
│   ├── analytics.ts
│   └── constants.ts
├── public/
│   ├── images/
│   ├── sitemap.xml
│   └── robots.txt
├── next.config.js
├── tailwind.config.js
├── vercel.json
└── package.json
```

---

## 14. Content Guidelines

### 14.1 Voice and Tone

- Professional but approachable
- Direct and clear (farriers are busy)
- Focus on time savings and efficiency
- Avoid jargon and technical terms
- Use "you" and "your" to address the reader

### 14.2 Messaging Hierarchy

1. **Primary:** Route optimization saves time
2. **Secondary:** Works offline for rural areas
3. **Tertiary:** Simple invoicing and tracking
4. **Supporting:** Calendar sync, mileage tracking

### 14.3 Call-to-Action Guidelines

- Primary CTA: Always "Download on Google Play" with icon
- Secondary CTA: "See Pricing" or "Learn More"
- Avoid multiple competing CTAs in same section
