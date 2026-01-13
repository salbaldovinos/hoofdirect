# PRD-015: Marketing Website

**Priority**: P1  
**Phase**: 5 - Monetization  
**Estimated Duration**: 1 week

---

## Overview

### Purpose
Create a professional landing page at hoofdirect.com to convert visitors into app downloads.

### Business Value
- Primary conversion funnel
- SEO visibility for "farrier app" searches
- Establishes brand credibility
- Hosts pricing and feature information

### Success Metrics
| Metric | Target |
|--------|--------|
| Conversion rate (visit → download) | > 5% |
| Page load time | < 2 seconds |
| SEO ranking for "farrier app" | Top 10 |

---

## Technical Specification

**Stack**: Next.js 14 (App Router), Tailwind CSS, Vercel

---

## Page Structure

### Homepage Sections

1. **Hero**
   - Headline: "Route-Optimized Scheduling for Professional Farriers"
   - Subhead: "Save 5+ hours/week with intelligent route planning"
   - CTA: [Download on Google Play]
   - Hero image: App screenshot

2. **Problem Statement**
   - Pain points farriers face
   - Manual route planning wastes time

3. **Features Grid**
   - Route Optimization (hero feature)
   - Calendar Sync
   - Offline Mode
   - Invoicing
   - Mileage Tracking
   - Client Management

4. **Pricing Table**
   ```
   Free          Solo $29/mo    Growing $79/mo    Multi $149/mo
   ────────────────────────────────────────────────────────────
   10 clients    Unlimited      Unlimited         Unlimited
   No routes     8 stops/day    15 stops/day      Unlimited
   0 SMS         50 SMS/mo      200 SMS/mo        500 SMS/mo
   1 user        1 user         2 users           5 users
   ```

5. **Testimonials** (post-launch)

6. **FAQ**
   - Does it work offline?
   - How does route optimization work?
   - Can I try before I buy?

7. **Footer**
   - Privacy Policy
   - Terms of Service
   - Contact: support@hoofdirect.com

---

## SEO Requirements

- Meta title: "Hoof Direct - Route-Optimized Farrier Scheduling App"
- Meta description: "Save hours every week with intelligent route planning..."
- Structured data (SoftwareApplication schema)
- Sitemap.xml
- robots.txt

---

## Acceptance Criteria

| ID | Criteria | Verification |
|----|----------|--------------|
| AC-015-01 | Page loads < 2s | Lighthouse |
| AC-015-02 | Mobile responsive | Manual test |
| AC-015-03 | Play Store link works | Manual test |
| AC-015-04 | Privacy/Terms pages exist | Manual test |

---

## Dependencies

| Dependency | Type | Status |
|------------|------|--------|
| Domain (hoofdirect.com) | External | Required |
| Vercel account | External | Required |
