# JUGAAD — Phase 2: Premium UI Redesign Requirements
# Target: Material 3 / Jetpack Compose

Last updated: 2026-05-06 (rev 4 — AI architecture definition, MVI layer, gesture system, performance patterns)

---

## Objective

Elevate JUGAAD from a functional utility app to a premium household management product.
Redesign the main shell and Home screen in Jetpack Compose using Material 3 design language.
Keep existing Fragment/View screens running; only the shell navigation and Home screen are in scope for Phase 2.

---

## Scope

| In scope | Out of scope |
|---|---|
| MainActivity shell (nav rail + content area) | Wallet/Expenses Fragment internals |
| HomeFragment → HomeScreen composable | Meals Fragment internals |
| Color system tokens | Backend changes |
| Typography scale | Data layer / ViewModels |
| Spacing system | Play Store publishing |
| Quick Capture FAB | Settings / Import screens |

---

## R1 — Navigation Rail

### R1.1 Collapsed state (default)
- Width: **72dp** (updated from 64dp — M3 touch target compliance)
- Icons only, no labels in collapsed state
- Background: solid `#0F172A` (not gradient)
- Corner radius on right edge: 16dp
- Icon size: **24dp** (updated from 26dp — less crowded at 72dp rail)
- Inactive icon tint: `#9CA3AF` (60% opacity feel)

### R1.2 Active state
- Floating pill indicator: **44×44dp** (updated from 40dp — proper touch target)
- Background: `#22C55E` at 15% alpha
- Active icon tint: `#22C55E`
- No border, no outline — pill only
- Pill slides to new position on tab change (not instant jump)

### R1.3 Expanded state (tap to toggle)
- Width animates to **180dp** using spring (stiffness=Medium, dampingRatio=0.75)
- Labels appear with fade-in (200ms) after rail reaches full width
- Label style: 13sp Medium `#E5E7EB`
- **Collapse trigger: tap the active nav item again, OR navigate to a new tab**
- **No timer-based auto-collapse** — premium apps never auto-collapse UI without intent
- Toggle is on a dedicated expand icon at rail top, NOT on the nav items themselves
  (nav items only update selected destination; mixing toggle + selection creates unpredictable UX)

### R1.4 Icons (Material Symbols Rounded)
- Home → `cottage`
- Wallet → `account_balance_wallet`
- Meals → `restaurant`
- Documents → `folder_open`
- Family → `group`

### R1.5 Behavior
- Rail is always visible (no hide/show toggle needed in this phase)
- Bottom of rail: small avatar or initials chip for current user
- No bottom navigation bar on compact screens (rail only, all screen sizes)

---

## R2 — Home Screen Hero Card

### R2.1 Screen background
- Color: `#F8FAFC`
- No image, no pattern

### R2.2 Greeting row
- Left: "Good morning, [Name]" — 14sp Regular `#6B7280`
- Right: current date — 13sp Regular `#9CA3AF`
- Margin top: 24dp from status bar
- No bold, no icons in this row

### R2.3 Hero balance card
- Surface: `#FFFFFF`, elevation 2dp (M3 tonal surface)
- Corner radius: 20dp
- Border: 1dp `#E5E7EB`
- Padding: 20dp all sides
- **Content layout (focused — hero shows only what matters):**
  - Row 1: "Total this month" label — 12sp `#9CA3AF`, Title Case with `letterSpacing=0.08em`
  - Row 2: Balance amount — 32sp SemiBold `#0F172A`, tabular nums font feature
  - Row 3: Delta chip only
    - Delta: "↓ 12% vs last month" — filled chip, green background if positive, red background if negative
    - Text: 12sp Medium, matching color
  - **Row 4 removed** — meals and alerts chips moved to their own sections below
  - Rationale: hero = focus, not a dashboard dump. One number, one signal.

### R2.4 Chip style (status chips)
- Height: 28dp
- Corner radius: 14dp (fully rounded)
- Background: color at 12% alpha
- Text: 12sp Medium, matching color at 100%
- Icon: 14dp, same color

---

## R3 — Today + Alerts Section

### R3.1 Layout (adaptive)
- **Phone (screenWidthDp ≤ 600):** stacked vertically, full width cards
- **Tablet (screenWidthDp > 600):** side by side, `weight=1` each
- Each card: corner radius=16dp, background `#FFFFFF`, elevation 1dp, border 1dp `#F3F4F6`
- Spacing between cards: 12dp
- Margin top from hero card: 24dp

```kotlin
if (screenWidthDp > 600) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TodayCard(Modifier.weight(1f))
        AlertsCard(Modifier.weight(1f))
    }
} else {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TodayCard()
        AlertsCard()
    }
}
```

### R3.2 Today card
- Header: "TODAY" — 11sp Medium `#9CA3AF`
- Item rows: bullet + event name + time
- Max 2 items visible, remainder hidden behind "+ N more" tap
- "+ N more" — 12sp `#6B7280`, tappable, expands inline

### R3.3 Alerts card
- Header: "Alerts" with badge count — 11sp Medium `#9CA3AF`, Title Case
- Badge: 16dp circle `#F97316`, white number inside
- Item rows: icon + short text
- **Urgent items (rent, expiry):**
  - Left border 3dp `#F97316`
  - Background tint: `#F97316` at 6% alpha (border alone is a weak signal)
  - Icon color: `#F97316`
- Normal items: no border, no tint

---

## R4 — Module Grid

### R4.1 Layout (adaptive)
- **Phone (screenWidthDp ≤ 600): 2 columns** — 3 columns is too tight for safe tap targets on phones
- **Tablet (screenWidthDp > 600): 3 columns**
- Margin top from Today/Alerts: 24dp
- Section header: "Modules" — 11sp Medium `#9CA3AF`, Title Case with `letterSpacing=0.08em`

```kotlin
val columns = if (screenWidthDp > 600) 3 else 2
LazyVerticalGrid(columns = GridCells.Fixed(columns), ...)
```

### R4.2 Module card
- Size: fills column, ~80dp tall
- Background: `#FFFFFF`, corner radius 16dp, border 1dp `#F3F4F6`
- Content (vertical, center-aligned):
  - Icon: 28dp, tinted with module color
  - Label: 13sp Medium `#0F172A`
  - Subtitle: 11sp `#9CA3AF` (e.g. "€243 spent", "2 planned")
- Tap: navigate to that tab
- Long press: show bottom sheet with quick actions for that module

### R4.3 Module colors
- Wallet → `#6F3DF0` (purple)
- Meals → `#22C55E` (green)
- Documents → `#3B82F6` (blue)
- Family → `#F97316` (orange)
- Recipes → `#8B5CF6` (violet)

---

## R5 — Quick Capture FAB (Speed Dial)

### R5.1 Main FAB
- Position: bottom-right, 16dp margins
- Size: 56dp, background `#22C55E`, icon `add` white
- On tap: expand speed dial upward
- **Collapse triggers: second tap on FAB, back press, or tap outside**
- **No timer-based auto-dismiss** — same principle as nav rail

### R5.2 Speed dial items (3 mini-FABs)
- Appear with staggered animation (80ms delay between each)
- Each: 40dp mini-FAB + label chip to the left
- Order bottom to top:
  1. Scan — icon `document_scanner`, label "Scan receipt"
  2. Speak — icon `mic`, label "Voice note"
  3. Parse — icon `inbox`, label "Parse inbox"
- Label chip: 12sp, background `#0F172A` at 85%, white text, corner radius 8dp

### R5.3 Animation
- Expand: items slide up + fade in, spring curve, 80ms stagger
- Collapse: reverse, 60ms stagger
- Main FAB icon rotates 45° when expanded (add → close)

### R5.4 Implementation caveat + correct pattern
- The FAB's `Box(Modifier.fillMaxSize())` wrapper will intercept touch events from content below when expanded
- Fix: render a scrim `Box` with `matchParentSize()` + `background(Black 20% alpha)` + `pointerInput detectTapGestures` ONLY when expanded
- Do NOT use `fillMaxSize()` on the outer container unconditionally
- Add `BackHandler(enabled = expanded) { expanded = false }` for back press dismiss

**Stagger implementation — use `StaggeredMiniFab` composable (not inline LaunchedEffect):**
```kotlin
// WRONG — LaunchedEffect inside loop delays but does not stagger visibility
LaunchedEffect(expanded) { delay(index * 80L) }
MiniFab(item)

// CORRECT — extract to a composable that owns its own visibility state
@Composable
fun StaggeredMiniFab(
    visible: Boolean,
    index: Int,
    content: @Composable () -> Unit
) {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) { delay(index * 80L); show = true }
        else { show = false }
    }
    AnimatedVisibility(
        visible = show,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit  = fadeOut() + slideOutVertically()
    ) {
        content()
    }
}

// Usage in QuickCaptureFab:
listOf("Scan", "Speak", "Parse").forEachIndexed { index, item ->
    StaggeredMiniFab(visible = expanded, index = index) {
        MiniFab(item)
    }
}
```

---

## R6 — Typography Scale

| Role | Size | Weight | Color token |
|---|---|---|---|
| Balance (hero) | 32sp | SemiBold | `text_primary` `#0F172A` |
| Screen title | 20sp | SemiBold | `text_primary` |
| Card section title | 16sp | Medium | `text_primary` |
| Body text | 14sp | Regular | `text_primary` |
| Secondary body | 14sp | Regular | `text_secondary` `#6B7280` |
| Meta / label | 12sp | Regular | `text_muted` `#9CA3AF` |
| Section header | 11sp | Medium | `text_muted`, Title Case, `letterSpacing=0.08em` |
| Chip text | 12sp | Medium | varies by chip |

Tabular nums feature (`fontFeatureSettings="tnum"`) must be applied to all currency amounts.

### R6.1 Font decision
- **Font: Roboto Flex** (variable font, best for Android, supports weight axes)
- Import as `res/font/roboto_flex.ttf`
- Use `fontVariationSettings` to control weight per text style
- This enables smooth weight variation at different sizes without loading multiple font files
- Fallback: system default `Roboto` if Roboto Flex import is blocked by build constraints

---

## R7 — Color System Tokens

### Surfaces
| Token | Hex | Use |
|---|---|---|
| `bg_base` | `#F8FAFC` | Screen background |
| `surface` | `#FFFFFF` | Cards |
| `surface_variant` | `#F3F4F6` | Subtle backgrounds |
| `nav_bg` | `#0F172A` | Nav rail |
| `border` | `#E5E7EB` | Card borders |

### Accent (Phase 2 discipline — 3 active accents, 2 deferred)
| Token | Hex | Use | Phase |
|---|---|---|---|
| `green` | `#22C55E` | Primary actions, FAB, positive delta | **Phase 2** |
| `green_soft` | `#DCFCE7` | Chip backgrounds | **Phase 2** |
| `purple` | `#8B5CF6` | Module color: Meals, health | **Phase 2** |
| `purple_soft` | `#EDE9FE` | Module chip bg | **Phase 2** |
| `orange` | `#F97316` | Warnings, alerts | **Phase 2** |
| `orange_soft` | `#FFF7ED` | Alert tint | **Phase 2** |
| `red` | `#EF4444` | Negative delta on hero card (required) | **Phase 2** |
| `red_soft` | `#FEF2F2` | Negative delta chip bg | **Phase 2** |
| `blue` | `#3B82F6` | Info, documents | **Deferred to Phase 3** |
| `blue_soft` | `#EFF6FF` | | **Deferred to Phase 3** |

> Note: Red is kept in Phase 2 because it is required for the hero card negative delta chip.
> Blue is deferred — risk of visual noise outweighs benefit at this stage.

### Text
| Token | Hex |
|---|---|
| `text_primary` | `#0F172A` |
| `text_secondary` | `#6B7280` |
| `text_muted` | `#9CA3AF` |
| `text_on_dark` | `#F9FAFB` |
| `text_on_color` | `#FFFFFF` |

---

## R8 — Spacing System

Use 8dp base grid. Standard values:

| Name | Value | Use |
|---|---|---|
| `xs` | 4dp | Icon padding, tight gaps |
| `sm` | 8dp | Between related items |
| `md` | 16dp | Card padding, standard gap |
| `lg` | 24dp | Between sections |
| `xl` | 32dp | Top of screen, major separators |

---

## R9 — Micro-interactions

| Trigger | Behavior |
|---|---|
| Button/card press | `scale(0.97)` → spring back, duration 150ms |
| Nav rail item tap | Pill indicator slides to new position (not instant jump) |
| Nav rail expand | Width spring animation, labels fade in after rail settles |
| FAB expand | Staggered slide-up + fade-in |
| Alert badge | Slow alpha pulse (0.7 → 1.0, 1200ms loop) on urgent items |
| Card tap (module grid) | Ripple + slight elevation increase |

### R9.1 Page / tab transition spec
| Transition | Behavior |
|---|---|
| Tab switch (nav rail) | Fade + 8dp horizontal slide, 200ms, `FastOutSlowInEasing` |
| Home → module screen | Shared element scale on module card icon if possible, else crossfade |
| FAB action → new screen | Slide up from bottom, 280ms spring |

**Implementation note:** Transitions belong in `composable()` `enterTransition`/`exitTransition` params, NOT in an outer `AnimatedContent` wrapping `NavHost`. Wrapping `NavHost` in `AnimatedContent(targetState = currentRoute)` causes full nav host re-composition on every route change.

---

## R10 — Motion System

### R10.1 Motion principles (apply everywhere)
- Fast in → slow out
- Small distances only (4–24dp max travel)
- Spring curves, not linear
- State continuity over flashiness — animate state changes, not decoration

### R10.2 Motion.kt — central token file
All animation specs must come from this file. No hardcoded `tween(200)` inline.

```kotlin
object Motion {
    val SpringDefault = spring<Float>(
        dampingRatio = 0.8f,
        stiffness = Spring.StiffnessMedium
    )
    val SpringSnappy = spring<Float>(
        dampingRatio = 0.7f,
        stiffness = Spring.StiffnessHigh
    )
    // Note: tween<Float> for alpha/scale, tween<IntOffset> for positional slide
    val FadeIn  = tween<Float>(durationMillis = 180, easing = FastOutSlowInEasing)
    val FadeOut = tween<Float>(durationMillis = 120)
    val Slide   = tween<IntOffset>(durationMillis = 220, easing = FastOutSlowInEasing)
}
```

### R10.3 pressEffect() — global press feedback Modifier
Apply to every tappable surface: cards, buttons, grid items, FAB mini items.

```kotlin
fun Modifier.pressEffect(): Modifier = composed {
    val scale = remember { Animatable(1f) }
    this
        .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    scale.animateTo(0.96f, Motion.SpringSnappy)
                    tryAwaitRelease()
                    scale.animateTo(1f, Motion.SpringDefault)
                }
            )
        }
}
```

### R10.4 Nav rail — animated width + label alpha
```kotlin
val width by animateDpAsState(
    targetValue = if (expanded) 180.dp else 72.dp,
    animationSpec = Motion.SpringDefault
)
val labelAlpha by animateFloatAsState(
    targetValue = if (expanded) 1f else 0f,
    animationSpec = tween(200)
)
// Labels render only when alpha > 0 to avoid invisible tap targets
if (expanded || labelAlpha > 0f) {
    Text(label, modifier = Modifier.alpha(labelAlpha))
}
```

### R10.5 Pill indicator — smooth positional animation
This is a high-signal premium detail. Pill slides between items rather than jumping.

```kotlin
// Calculate pill offset from selected index
val offsetY by animateDpAsState(
    targetValue = selectedIndex * 56.dp,  // 56dp = item height
    animationSpec = Motion.SpringDefault
)
// Draw pill as a Box overlay positioned with offsetY
Box(
    modifier = Modifier
        .offset(y = offsetY)
        .size(44.dp)
        .clip(CircleShape)
        .background(Color(0xFF22C55E).copy(alpha = 0.15f))
)
```

### R10.6 Screen transitions — correct placement
**Use global `NavHost` transition params or per-`composable()` params. NEVER wrap `NavHost` in `AnimatedContent`.**

Preferred: set global defaults on `NavHost` and override per-destination only when needed:
```kotlin
NavHost(
    navController = navController,
    startDestination = Screen.Home.route,
    enterTransition = {
        fadeIn(tween(180)) + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(220))
    },
    exitTransition = {
        fadeOut(tween(120)) + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(220))
    },
    popEnterTransition = {
        fadeIn(tween(180)) + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(220))
    },
    popExitTransition = {
        fadeOut(tween(120)) + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(220))
    }
) { ... }
```
`slideIntoContainer` is preferred over `slideInHorizontally` — it uses the container bounds, not screen width, so the travel distance is context-correct.

### R10.7 Edge swipe gesture — expand/collapse rail
Swipe from left edge to expand rail; swipe back to collapse. Applied to the outer `Row` in `AppShell`.
```kotlin
fun Modifier.edgeSwipeRail(
    expanded: Boolean,
    onExpand: () -> Unit,
    onCollapse: () -> Unit
): Modifier = pointerInput(expanded) {  // key on expanded to re-attach correctly
    detectHorizontalDragGestures { _, dragAmount ->
        if (!expanded && dragAmount > 30f) onExpand()
        else if (expanded && dragAmount < -30f) onCollapse()
    }
}
// Apply in AppShell:
Row(modifier = Modifier.edgeSwipeRail(expanded, onExpand = { expanded = true }, onCollapse = { expanded = false })) { ... }
```

### R10.8 Drag-to-reposition FAB (Phase 2 stretch)
Allows power users to reposition the FAB — matches Google Maps / Bubbles pattern.
```kotlin
val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
FloatingActionButton(
    modifier = Modifier
        .offset { IntOffset(offset.value.x.toInt(), offset.value.y.toInt()) }
        .pointerInput(Unit) {
            detectDragGestures { change, dragAmount ->
                change.consume()
                // snapTo is fine during drag; animate back to grid-snap on release
                scope.launch { offset.snapTo(offset.value + dragAmount) }
            }
        },
    onClick = { expanded = !expanded }
) { ... }
```

---

## R11 — Elevation Hierarchy (previously missing)

Do not rely solely on borders for depth. Use M3 tonal elevation consistently:

| Component | Elevation | Effect |
|---|---|---|
| Screen background | 0dp | Flat `#F8FAFC` |
| Cards (Today, Alerts, Modules) | 1dp | Barely lifted |
| Hero card | 2dp | Clearly above page |
| Bottom sheets | 2dp | |
| FAB | 3dp | Highest |
| Nav rail | Flat (color contrast does the work) | No shadow needed |

---

## R12 — Navigation Architecture

### R12.1 Route model
```kotlin
sealed class Screen(val route: String, val icon: ImageVector, val label: String) {
    object Home   : Screen("home",   Icons.Rounded.Cottage,               "Home")
    object Wallet : Screen("wallet", Icons.Rounded.AccountBalanceWallet,  "Wallet")
    object Meals  : Screen("meals",  Icons.Rounded.Restaurant,            "Meals")
    object Docs   : Screen("docs",   Icons.Rounded.FolderOpen,            "Documents")
    object Family : Screen("family", Icons.Rounded.Group,                 "Family")
    companion object { val all = listOf(Home, Wallet, Meals, Docs, Family) }
}
```

### R12.2 Dependency
```kotlin
implementation("androidx.navigation:navigation-compose:2.7.7")
```

### R12.3 AppNavHost — global NavHost transitions
Set transitions globally on `NavHost`. Override per-`composable()` only for special cases.
```kotlin
@Composable
fun AppNavHost(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        enterTransition = {
            fadeIn(tween(180)) +
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(220))
        },
        exitTransition = {
            fadeOut(tween(120)) +
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(220))
        },
        popEnterTransition = {
            fadeIn(tween(180)) +
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(220))
        },
        popExitTransition = {
            fadeOut(tween(120)) +
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(220))
        }
    ) {
        Screen.all.forEach { screen ->
            composable(route = screen.route) {
                when (screen) {
                    Screen.Home -> HomeScreen()
                    else        -> LegacyFragmentHost(screen.route)
                }
            }
        }
    }
}
```

### R12.4 AppShell — navigation-driven
```kotlin
@Composable
fun AppShell() {
    val navController = rememberNavController()
    // derivedStateOf: only recomposes consumers when route string actually changes
    // NOT on every back-stack entry change (which includes state saves etc.)
    val currentRoute by remember {
        derivedStateOf { navController.currentBackStackEntry?.destination?.route }
    }

    Row {
        NavigationRailComposable(
            currentRoute = currentRoute,
            onNavigate = { route ->
                navController.navigate(route) {
                    popUpTo(Screen.Home.route) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        )
        // No AnimatedContent wrapper here — transitions live in NavHost composable() params
        AppNavHost(navController)
    }

    BackHandler {
        if (!navController.popBackStack()) {
            // activity.finish() — wire in Activity context
        }
    }
}
```

### R12.5 Deep link scaffold (zero-cost future-proofing)
```kotlin
composable(
    route = Screen.Wallet.route,
    deepLinks = listOf(navDeepLink { uriPattern = "jugaad://wallet" })
) { ... }
```

### R12.6 State preservation
- Use `rememberSaveable` for all UI state (selected tab, FAB expanded, scroll position)
- `saveState = true` + `restoreState = true` in `navigate()` preserves tab state on back-stack pop

---

## R13 — AI Insights Layer

### R13.0 What "AI" means in JUGAAD (important: not on-device model)

In JUGAAD, "AI" is **not** a model running inside the Android app. It is a server-side intelligence pipeline that converts raw household signals into structured, actionable JSON that the UI simply renders.

```
Data sources (email, receipts, calendar, bank)
    ↓
n8n workflow engine (parsers + rules + optional LLM call)
    ↓
Insight JSON  →  Android API  →  ViewModel  →  InsightCard
```

The app has three roles:
- **Data collector** — Android sends signals (receipts, transactions, meal logs)
- **Decision system** — n8n processes signals → produces insights
- **Experience layer** — Compose renders insight cards

### R13.1 Insight JSON contract
All insights from the backend must conform to this schema:
```json
{
  "id": "ins_abc123",
  "type": "WARNING",
  "category": "SUBSCRIPTION",
  "priority": "HIGH",
  "title": "Netflix subscription detected",
  "message": "₹12.99 monthly recurring charge detected",
  "action": "VIEW_WALLET",
  "expiresAt": "2026-05-08T00:00:00Z"
}
```
- `type`: `WARNING` | `INFO` | `SUCCESS`
- `priority`: `HIGH` | `MEDIUM` | `LOW` (UI picks the highest-priority to surface)
- `action`: deep-link target — maps to `Screen.route` values
- `expiresAt`: optional — insight auto-dismissed after this timestamp

### R13.2 n8n workflow examples
| Trigger | n8n flow | Insight produced |
|---|---|---|
| Bank email received | text extract → LLM classify → rule check | "Netflix detected — ₹12.99" |
| Grocery receipt scanned | OCR → categorize → budget compare | "Grocery +18% vs last week" |
| Calendar event | read meal plan → conflict check | "Dinner overlaps meeting at 8PM" |
| Contract PDF uploaded | text extract → date parse | "Renewal risk: 12 days" |

### R13.3 InsightCard component
- Displayed at top of HomeScreen, above greeting row
- Hidden when no insights are available (do not show empty state)
- Maximum 1 insight visible at a time (most urgent wins)
- Dismiss: swipe left or tap X

### R13.4 Insight types
| Type | Color | Example |
|---|---|---|
| `WARNING` | `#F97316` | "2 subscriptions due this week" |
| `INFO` | `#8B5CF6` | "Protein goal not met yesterday" |
| `SUCCESS` | `#22C55E` | "You're 18% under budget this month" |

### R13.5 Design spec
- Border: 1dp, insight color at 20% alpha
- Background: insight color at 8% alpha
- Text: 14sp Regular, insight color at 100%
- Corner radius: 12dp
- Left edge: 3dp solid bar in insight color
- Icon: 16dp, insight color, left of text

### R13.6 Data source (Phase 2: static/mock, Phase 3: live)
- Phase 2: hardcoded `Insight` object in ViewModel for UI validation
- Phase 3: `InsightRepository` calls `GET /api/v1/insights` — backend returns the JSON contract above
- Future: n8n workflows push insights via webhook to the backend; backend stores and serves them

---

## R14 — Performance Patterns (Apply During Implementation)

Compose performance issues are silent — no warnings, just janky UI. Apply these proactively.

### R14.1 `derivedStateOf` for derived UI state
Prevent over-recomposition from high-frequency state changes:
```kotlin
// BAD — recomposes on every back-stack change (including invisible saves)
val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

// GOOD — recomposes only when route string value changes
val currentRoute by remember {
    derivedStateOf { navController.currentBackStackEntry?.destination?.route }
}
```
Also use for: scroll-derived values, list filter results, any computed state from another state.

### R14.2 `@Immutable` / `@Stable` on data models
Without these, Compose cannot skip recomposition of composables that take these as params:
```kotlin
@Immutable
data class Module(val id: String, val title: String, val icon: ImageVector, val color: Color)

@Immutable
data class Insight(val id: String, val type: InsightType, val message: String, val action: String)
```

### R14.3 `key` in `LazyVerticalGrid` / `LazyColumn`
Prevents full list re-render when items reorder or update:
```kotlin
items(modules, key = { it.id }) { module ->
    ModuleCard(module)
}
```

### R14.4 `remember { MutableInteractionSource() }` everywhere
Forgetting `remember` recreates the object on every recomposition:
```kotlin
// BAD
clickable(interactionSource = MutableInteractionSource(), ...)

// GOOD
val interactionSource = remember { MutableInteractionSource() }
clickable(interactionSource = interactionSource, ...)
```

### R14.5 `snapshotFlow` for reactive state → side effects
Use to bridge Compose state into coroutine flow for triggering insight refreshes:
```kotlin
LaunchedEffect(Unit) {
    snapshotFlow { uiState.activeTab }
        .distinctUntilChanged()
        .collect { tab ->
            if (tab == Screen.Home.route) viewModel.handle(HomeIntent.RefreshInsights)
        }
}
```

### R14.6 Haptic feedback
```kotlin
val haptic = LocalHapticFeedback.current
// Use on: FAB open, long press on module card, critical destructive actions
haptic.performHapticFeedback(HapticFeedbackType.LongPress)
```

### R14.7 Scroll-based header effects (stretch)
- Hero card: slight elevation increase as user scrolls down (tonal surface level 2→3)
- Greeting row: fade out at 20% scroll depth
- Implementation: `LazyColumn` with `rememberLazyListState()` + derive scroll fraction via `derivedStateOf`

### R14.8 Shared element continuity (Phase 2 stretch, Phase 3 full)
- Target: Home module card icon → destination screen icon (shared element transition)
- Phase 2: implement crossfade fallback
- Phase 3: add Compose shared element API when stable

---

## R15 — Advanced Polish (Phase 2 stretch goals)

### R15.1 Pull-to-reveal insights
Instead of always-visible insight card: pull down to reveal. Keeps home screen uncluttered.
- Phase 2: standard `pullRefresh` pattern triggers insight re-fetch
- Phase 3: custom drag-to-reveal with `AnchoredDraggable`

### R15.2 Drag-to-reposition FAB
See R10.8 for implementation pattern.

### R15.3 Edge swipe to expand rail
See R10.7 for implementation pattern.

---

## R16 — Full System Architecture

This section defines the complete production architecture — Android layers + AI layer + data flow.

### R16.1 Layer diagram
```
Android App (JUGAAD)
├── presentation/
│   ├── compose/          ← Compose UI (AppShell, HomeScreen, components)
│   ├── state/            ← MVI (HomeState, HomeIntent, HomeViewModel)
│   └── navigation/       ← Screen, AppNavHost, AppShell
├── domain/
│   ├── models/           ← Insight, Module, Balance (pure Kotlin, no Android deps)
│   ├── usecases/         ← GetInsightsUseCase, GetBalanceUseCase
│   └── repositories/     ← InsightRepository interface
├── data/
│   ├── api/              ← Retrofit interface + InsightDto
│   ├── repository/       ← InsightRepositoryImpl (maps DTO → domain model)
│   └── local/            ← Room entities (existing household_app.db)
└── core/
    ├── Result.kt         ← sealed class Result<T> (Loading, Success, Error)
    └── motion/           ← Motion.kt

Backend (Python/Flask on api.jugaad.app)
└── /api/v1/insights      ← GET returns Insight JSON list (R13.1 contract)

n8n (AI layer)
├── Email parser workflow
├── Receipt OCR workflow
├── Calendar conflict workflow
└── → POST insights to backend DB
```

### R16.2 MVI state pattern for HomeScreen
```kotlin
// State
@Immutable
data class HomeState(
    val balance: String = "",
    val deltaPercent: Float = 0f,
    val insights: List<Insight> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

// Intents
sealed class HomeIntent {
    object Load : HomeIntent()
    object RefreshInsights : HomeIntent()
    data class DismissInsight(val id: String) : HomeIntent()
}

// ViewModel
class HomeViewModel(
    private val insightRepo: InsightRepository
) : ViewModel() {
    private val _state = MutableStateFlow(HomeState())
    val state = _state.asStateFlow()

    fun handle(intent: HomeIntent) = when (intent) {
        is HomeIntent.Load             -> loadAll()
        is HomeIntent.RefreshInsights  -> loadInsights()
        is HomeIntent.DismissInsight   -> dismissInsight(intent.id)
    }

    private fun loadInsights() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            insightRepo.getInsights()
                .onSuccess { insights -> _state.update { it.copy(insights = insights, loading = false) } }
                .onFailure { err    -> _state.update { it.copy(error = err.message, loading = false) } }
        }
    }

    private fun dismissInsight(id: String) {
        _state.update { it.copy(insights = it.insights.filterNot { i -> i.id == id }) }
    }
}
```

### R16.3 Insight repository
```kotlin
interface InsightRepository {
    suspend fun getInsights(): Result<List<Insight>>
}

class InsightRepositoryImpl(
    private val api: JugaadApi
) : InsightRepository {
    override suspend fun getInsights(): Result<List<Insight>> =
        runCatching { api.getInsights().map { it.toDomain() } }
}
```

### R16.4 Full data flow (end to end)
```
1. Household event occurs (bank email, receipt scan, calendar update)
2. n8n workflow triggers, processes signal, calls optional LLM
3. n8n POSTs structured Insight JSON to backend /api/v1/insights
4. Android: HomeViewModel.handle(HomeIntent.Load) → InsightRepository.getInsights()
5. Retrofit fetches from backend → InsightDto list returned
6. Repository maps Dto → domain Insight
7. ViewModel updates HomeState.insights
8. Compose recomposes: highest-priority Insight rendered in InsightCard
9. User taps action → navController.navigate(insight.action)
```

---

## R17 — Reference Implementation

Approved Compose code patterns to use as baseline during implementation.

### Theme.kt
```kotlin
@Composable
fun JugaadTheme(content: @Composable () -> Unit) {
    val colors = lightColorScheme(
        primary = Color(0xFF22C55E),
        background = Color(0xFFF8FAFC),
        surface = Color.White,
        onPrimary = Color.White,
        onBackground = Color(0xFF0F172A)
    )
    MaterialTheme(
        colorScheme = colors,
        typography = Typography(
            headlineLarge = TextStyle(
                fontSize = 32.sp,
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = "tnum"
            ),
            bodyMedium = TextStyle(fontSize = 14.sp),
            labelSmall = TextStyle(fontSize = 12.sp)
        ),
        content = content
    )
}
```

### AppShell.kt (nav rail toggle separation)
```kotlin
// IMPORTANT: toggle (expand/collapse) must be on a separate icon button
// NOT on nav items — mixing toggle + selection causes unpredictable expand state
var selected by remember { mutableStateOf(0) }
var expanded by remember { mutableStateOf(false) }

// Nav item click: update selected ONLY
NavigationRailItem(
    selected = selected == index,
    onClick = { selected = index },  // no toggle here
    ...
)

// Separate toggle button at top of rail
IconButton(onClick = { expanded = !expanded }) {
    Icon(if (expanded) Icons.Default.ChevronLeft else Icons.Default.ChevronRight, ...)
}
```

### QuickCaptureFab.kt (scrim for tap-outside dismiss)
```kotlin
// Scrim uses matchParentSize() (not fillMaxSize) — only valid inside a Box
// pointerInput + detectTapGestures preferred over clickable for touch interception
if (expanded) {
    Box(
        modifier = Modifier
            .matchParentSize()
            .background(Color.Black.copy(alpha = 0.2f))
            .pointerInput(Unit) {
                detectTapGestures { expanded = false }
            }
    )
}
BackHandler(enabled = expanded) { expanded = false }
```

### InsightCard.kt
```kotlin
enum class InsightType { WARNING, INFO, SUCCESS }

@Composable
fun InsightCard(message: String, type: InsightType, onDismiss: () -> Unit) {
    val color = when (type) {
        InsightType.WARNING -> Color(0xFFF97316)
        InsightType.INFO    -> Color(0xFF8B5CF6)
        InsightType.SUCCESS -> Color(0xFF22C55E)
    }
    Card(
        border = BorderStroke(1.dp, color.copy(alpha = 0.2f)),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.width(3.dp).height(40.dp).background(color))
            Spacer(Modifier.width(12.dp))
            Text(message, color = color, fontSize = 14.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = color)
            }
        }
    }
}
```

---

## R18 — Implementation Approach

### Tech stack
- Jetpack Compose for HomeScreen + shell navigation
- Existing Fragment screens embedded via `AndroidView` / `ComposeView` interop
- NavigationRailItem from `androidx.compose.material3`
- Material 3 theming via `MaterialTheme` with custom `ColorScheme`
- Font: Roboto Flex (`res/font/roboto_flex.ttf`)

### Migration strategy
1. Add Compose + Material3 dependencies to `android/build.gradle.kts`
2. Add Roboto Flex font to `res/font/`
3. Create `ui/theme/` package: `Color.kt`, `Type.kt`, `Theme.kt`
4. Create `ui/compose/HomeScreen.kt`
5. Create `ui/compose/AppShell.kt` (nav rail + content host)
6. Replace `activity_main.xml` inflation with `setContent { JugaadTheme { AppShell() } }`
7. Keep existing Fragment destinations behind `ComposeView` wrappers until migrated

### File targets
```
android/src/main/java/com/household/app/
  ui/
    compose/
      AppShell.kt              ← nav rail + NavHost host (no AnimatedContent wrapper)
      AppNavHost.kt            ← NavHost with per-route transitions
      HomeScreen.kt            ← full home screen
      components/
        HeroCard.kt
        InsightCard.kt         ← AI insights layer
        ModuleGrid.kt          ← adaptive 2/3 col
        TodayAlertsRow.kt      ← adaptive stacked/side-by-side
        QuickCaptureFab.kt     ← speed dial with per-item AnimatedVisibility + scrim
        NavigationRailComposable.kt  ← rail with animated pill + dedicated toggle
      navigation/
        Screen.kt              ← sealed class route definitions
      motion/
        Motion.kt              ← central animation token file
      theme/
        Color.kt
        Type.kt
        Theme.kt
android/src/main/res/font/
  roboto_flex.ttf
```

---

## Acceptance Criteria

### Navigation
- [ ] App launches with dark nav rail (72dp, icon-only)
- [ ] Nav rail expands via dedicated toggle button, not nav item tap
- [ ] Nav rail collapsed/expanded state persists within session
- [ ] No timer-based auto-collapse at any point

### Home Screen
- [ ] Insight card appears at top when insight data is present, hidden otherwise
- [ ] Hero card shows balance + delta chip only (no meals/alerts chips)
- [ ] Balance number uses tabular nums (digits align vertically)
- [ ] Delta chip is green for positive, red for negative
- [ ] Today and Alerts: stacked on phones (≤600dp), side-by-side on tablets
- [ ] Urgent alert items show background tint + colored icon, not just border
- [ ] Module grid: 2 columns on phones, 3 on tablets
- [ ] All "OPEN X" text buttons are gone — replaced by module cards
- [ ] Quick capture FAB expands on tap, collapses on second tap / back press / scrim tap
- [ ] No timer-based FAB auto-dismiss

### Motion
- [ ] All cards/buttons use `pressEffect()` Modifier (scale 0.96 on press)
- [ ] Nav rail width animates via `animateDpAsState` with spring curve
- [ ] Nav pill slides between items (not instant jump)
- [ ] FAB mini-items stagger individually via `StaggeredMiniFab` composable (not inline LaunchedEffect)
- [ ] Screen transitions use `slideIntoContainer` on `NavHost`, not outer `AnimatedContent` wrapper
- [ ] Edge swipe from left expands nav rail, swipe back collapses

### Navigation
- [ ] `sealed class Screen` defines all routes
- [ ] `NavHost` handles all navigation (no Fragment back-stack mixing on Home)
- [ ] `launchSingleTop = true` prevents duplicate destination stack entries
- [ ] Back press collapses FAB if expanded, else pops back stack, else finishes activity
- [ ] `saveState + restoreState` preserves tab state across navigation
- [ ] `currentRoute` derived via `derivedStateOf` (not raw `currentBackStackEntryAsState`)

### AI / Insights
- [ ] Insight JSON contract matches R13.1 schema
- [ ] `HomeViewModel` implements MVI pattern (R16.2) with `HomeState` + `HomeIntent`
- [ ] `InsightRepository` interface in domain layer; `Impl` in data layer
- [ ] Phase 2: hardcoded insight renders correctly in InsightCard
- [ ] `DismissInsight` intent removes insight from state without API call
- [ ] Insight `action` field navigates to correct `Screen.route`

### Performance
- [ ] All data models passed to composables annotated `@Immutable`
- [ ] `LazyVerticalGrid` items use `key = { it.id }`
- [ ] No `MutableInteractionSource()` without `remember`
- [ ] No inline hardcoded animation durations — all from `Motion.kt`

### Quality
- [ ] Green used only as accent (FAB, active nav, positive delta, success insight)
- [ ] All existing tabs (Wallet, Meals, Docs, Family) remain functional
- [ ] Build passes: `:android:assembleDebug` succeeds
- [ ] No regressions in existing backup/restore flows
- [ ] No visual regressions on Wallet/Expenses/Meals fragment screens
