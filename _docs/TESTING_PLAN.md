# Mini Numbers - Testing Plan

## Browser Matrix

| Browser | Windows | macOS | Linux | iOS | Android |
|---------|---------|-------|-------|-----|---------|
| Chrome (latest) | Yes | Yes | Yes | - | Yes |
| Firefox (latest) | Yes | Yes | Yes | - | - |
| Safari (latest) | - | Yes | - | Yes | - |
| Edge (latest) | Yes | - | - | - | - |

## Device Breakpoints

| Device | Width | Priority |
|--------|-------|----------|
| Desktop | 1440px+ | High |
| Small Desktop | 1024px-1440px | High |
| Tablet | 768px-1024px | Medium |
| Mobile | 320px-767px | Medium |

## Test Scenarios

### Authentication

- [ ] Login with valid credentials
- [ ] Login with invalid credentials (verify error message)
- [ ] Login with brute force protection (5+ failed attempts, verify lockout)
- [ ] Session persistence across page refresh
- [ ] Sign out clears session and redirects to login

### Setup Wizard

- [ ] First-run setup wizard displays correctly
- [ ] Salt generation produces valid 128-char hex string
- [ ] Configuration saves and initializes services without restart
- [ ] Setup redirects to login after completion

### Project Management

- [ ] Create project with valid name and domain
- [ ] API key is generated and displayed
- [ ] API key can be copied to clipboard
- [ ] Select project loads dashboard data
- [ ] Delete project removes all associated data
- [ ] Empty state shows when no projects exist

### Dashboard

- [ ] All stat cards display correct values
- [ ] Sparklines render for views and visitors
- [ ] Comparison metrics show percentage change
- [ ] Time filter switching updates all data
- [ ] Date range display updates correctly

### Charts & Visualizations

- [ ] Top Pages bar chart renders with data
- [ ] Referrers bar chart renders with data
- [ ] Browsers doughnut/bar toggle works and persists
- [ ] OS doughnut/bar toggle works and persists
- [ ] Devices doughnut/bar toggle works and persists
- [ ] Countries bar chart renders with data
- [ ] Countries map view renders with markers
- [ ] Country drill-down shows cities
- [ ] Time series line chart renders
- [ ] Activity heatmap renders with peak highlighting
- [ ] Contribution calendar renders 365 days
- [ ] Custom events section appears only when events exist
- [ ] Empty states show when no data exists for each chart

### Data Export

- [ ] CSV export works for each chart type
- [ ] Full report export includes all sections
- [ ] Raw events export with pagination
- [ ] Exported CSV files are valid and parseable

### Conversion Goals

- [ ] Create URL-based goal
- [ ] Create event-based goal
- [ ] Toggle goal active/inactive
- [ ] Delete goal
- [ ] Goal stats display conversion rates

### Funnels

- [ ] Create funnel with 2+ steps
- [ ] Funnel visualization shows drop-off percentages
- [ ] Delete funnel

### User Segments

- [ ] Create segment with single filter
- [ ] Create segment with multiple AND/OR filters
- [ ] Segment analysis returns filtered data
- [ ] Delete segment

### Live Feed

- [ ] Live feed updates every 5 seconds
- [ ] Live feed shows visitor path, location, and time
- [ ] Live feed stops when project is deselected

### Settings

- [ ] Time format toggle (12h/24h) applies to all timestamps
- [ ] Date format changes apply
- [ ] Heatmap color scheme changes
- [ ] Project rename works
- [ ] API key display and copy

### Onboarding

- [ ] Onboarding modal shows on first login with no projects
- [ ] Onboarding dismissal persists across sessions
- [ ] Checklist steps update based on project state

### Theme

- [ ] Light/dark toggle works
- [ ] Theme persists across sessions
- [ ] All charts and maps respect theme colors
- [ ] System `prefers-color-scheme` works on first visit

## Responsive Layout Testing

- [ ] Sidebar collapses on mobile (<640px)
- [ ] Mobile menu toggle shows/hides sidebar
- [ ] Stat cards stack vertically on mobile
- [ ] Charts resize correctly
- [ ] Tables scroll horizontally on small screens
- [ ] Modals are usable on mobile

## Accessibility Testing

- [ ] Tab navigation through all interactive elements
- [ ] Enter/Space activates buttons and links
- [ ] Skip-to-content link works
- [ ] ARIA labels present on all form controls
- [ ] Screen reader can navigate dashboard sections
- [ ] Color contrast meets WCAG AA (4.5:1 for text)
- [ ] Focus indicators are visible

## Performance Checks

- [ ] Initial page load < 3 seconds
- [ ] Time to interactive < 5 seconds
- [ ] API response times < 500ms for cached queries
- [ ] No memory leaks during extended use (live feed)
- [ ] Chart rendering < 200ms per chart
