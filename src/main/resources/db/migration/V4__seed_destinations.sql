-- V4: Seed Saint Martin destination — the first and only curated destination for Phase 0.
--
-- Aliases are stored pre-lowercased. The service lowercases user input before lookup,
-- so matching is always exact string equality — no LOWER() needed at query time.
--
-- Activities are ordered by priority DESC in queries. Priority 10 = most representative.
-- estimated_cost_* figures are per-person hints for the AI prompt; the budget engine
-- reads cost_rules (seeded in a later migration), not these columns.

INSERT INTO destinations (
    code, name, country, region, description,
    destination_type, recommended_min_days, recommended_max_days,
    best_for, local_tips, is_active
) VALUES (
    'SAINT_MARTIN',
    'Saint Martin',
    'Bangladesh',
    'Chittagong Division',
    'Saint Martin is Bangladesh''s only coral island, located at the southernmost tip of the country in the Bay of Bengal. The island is roughly 8 km² and is known for its crystal-clear turquoise water, coral reefs, white sandy beaches, and fresh seafood. It is separated from the mainland by a short ship journey from Teknaf. The island is divided into three sections: North Saint Martin (where most accommodation and facilities are concentrated), Middle Saint Martin, and Chera Dwip (a small tidal island accessible by boat, famous for its coral formations).',
    'Island / Beach',
    2,
    4,
    'beach, seafood, photography, relaxation, snorkeling, coral viewing, cycling, nature, sunrise, sunset',
    '1. Ship schedules from Teknaf depend on weather and season — always check departure times in advance and keep buffer time.
2. Carry sufficient cash; digital payment (cards, mobile banking) is not reliably available on the island.
3. Accommodation should be booked well in advance during peak season (November to March).
4. The sea can be rough during monsoon (June–September); some ships may not operate.
5. Chera Dwip (the southern tidal island) is only accessible by local boat; the crossing takes about 30 minutes.
6. The island has no ATM. Withdraw cash in Teknaf or Cox''s Bazar before boarding the ship.
7. Plastic and single-use items are restricted on the island to protect the coral ecosystem.
8. Fresh coconut water and locally caught fish are must-try items available throughout the island.',
    TRUE
);

-- Aliases — all lowercase so the service can match with a plain equality check
INSERT INTO destination_aliases (destination_id, alias)
SELECT id, 'saint martin'        FROM destinations WHERE code = 'SAINT_MARTIN';

INSERT INTO destination_aliases (destination_id, alias)
SELECT id, 'st. martin'          FROM destinations WHERE code = 'SAINT_MARTIN';

INSERT INTO destination_aliases (destination_id, alias)
SELECT id, 'st martin'           FROM destinations WHERE code = 'SAINT_MARTIN';

INSERT INTO destination_aliases (destination_id, alias)
SELECT id, 'saint martin island' FROM destinations WHERE code = 'SAINT_MARTIN';

INSERT INTO destination_aliases (destination_id, alias)
SELECT id, 'st. martin island'   FROM destinations WHERE code = 'SAINT_MARTIN';

INSERT INTO destination_aliases (destination_id, alias)
SELECT id, 'narikel jinjira'     FROM destinations WHERE code = 'SAINT_MARTIN';

-- Activities — ordered by priority (10 = highest) for prompt context truncation
INSERT INTO destination_activities (
    destination_id, title, description, category,
    estimated_cost_min, estimated_cost_max, recommended_duration_minutes, priority
)
SELECT
    id,
    'Beach Walk along the Shore',
    'A leisurely walk along Saint Martin''s main beach. The western beach offers calm, shallow water ideal for wading, while the eastern shore is rockier and better for photography. Best experienced at low tide.',
    'ACTIVITY',
    0.00, 0.00, 90, 10
FROM destinations WHERE code = 'SAINT_MARTIN';

INSERT INTO destination_activities (
    destination_id, title, description, category,
    estimated_cost_min, estimated_cost_max, recommended_duration_minutes, priority
)
SELECT
    id,
    'Chera Dwip (Coral Island) Day Trip',
    'A short boat ride to Chera Dwip, a small tidal island at the southern tip of Saint Martin. Known for its dense coral formations, starfish, sea anemones, and clear shallow water. The boat ride takes around 20–30 minutes each way. Entry fee applies.',
    'ACTIVITY',
    300.00, 600.00, 240, 10
FROM destinations WHERE code = 'SAINT_MARTIN';

INSERT INTO destination_activities (
    destination_id, title, description, category,
    estimated_cost_min, estimated_cost_max, recommended_duration_minutes, priority
)
SELECT
    id,
    'Sunrise at the Beach',
    'Saint Martin''s eastern shore is one of the best sunrise spots in Bangladesh. The sky turns vivid orange and pink over the Bay of Bengal. Ideal for photography. Best reached by a short walk from the accommodation area.',
    'ACTIVITY',
    0.00, 0.00, 60, 9
FROM destinations WHERE code = 'SAINT_MARTIN';

INSERT INTO destination_activities (
    destination_id, title, description, category,
    estimated_cost_min, estimated_cost_max, recommended_duration_minutes, priority
)
SELECT
    id,
    'Sunset Viewing',
    'The western beach of Saint Martin offers unobstructed views of the sunset over the Bay of Bengal. Many local teahouses and beachside restaurants set up chairs on the shore. Often accompanied by fresh coconut water.',
    'ACTIVITY',
    0.00, 0.00, 60, 9
FROM destinations WHERE code = 'SAINT_MARTIN';

INSERT INTO destination_activities (
    destination_id, title, description, category,
    estimated_cost_min, estimated_cost_max, recommended_duration_minutes, priority
)
SELECT
    id,
    'Fresh Seafood Dinner',
    'Saint Martin is famous for its seafood. Local restaurants along the beach serve freshly caught lobster, crab, fish, and shrimp cooked in Bangladeshi style. Prices are negotiated directly with restaurant owners. Eating on the beach after sunset is a highlight of any visit.',
    'FOOD',
    500.00, 2000.00, 90, 8
FROM destinations WHERE code = 'SAINT_MARTIN';

INSERT INTO destination_activities (
    destination_id, title, description, category,
    estimated_cost_min, estimated_cost_max, recommended_duration_minutes, priority
)
SELECT
    id,
    'Snorkeling and Coral Viewing',
    'The shallow reef areas around Saint Martin and Chera Dwip support visible coral and marine life. Snorkeling gear can be rented locally. The best spots are on the eastern and southern shores where the water is clear and coral is most dense.',
    'ACTIVITY',
    200.00, 500.00, 120, 8
FROM destinations WHERE code = 'SAINT_MARTIN';

INSERT INTO destination_activities (
    destination_id, title, description, category,
    estimated_cost_min, estimated_cost_max, recommended_duration_minutes, priority
)
SELECT
    id,
    'Island Cycling Tour',
    'Bicycles can be rented from several shops near the main jetty area. Cycling around the island takes 2–3 hours depending on stops. The north–south road passes through fishing villages, coconut groves, and beachside viewpoints.',
    'ACTIVITY',
    100.00, 300.00, 180, 7
FROM destinations WHERE code = 'SAINT_MARTIN';

INSERT INTO destination_activities (
    destination_id, title, description, category,
    estimated_cost_min, estimated_cost_max, recommended_duration_minutes, priority
)
SELECT
    id,
    'Boat Trip around the Island',
    'Local fishermen offer short boat trips around the island perimeter for small groups. The trip provides views of the coral reef from the water and is a good option for those who do not want to snorkel. Prices are negotiated at the jetty.',
    'ACTIVITY',
    300.00, 600.00, 120, 7
FROM destinations WHERE code = 'SAINT_MARTIN';

INSERT INTO destination_activities (
    destination_id, title, description, category,
    estimated_cost_min, estimated_cost_max, recommended_duration_minutes, priority
)
SELECT
    id,
    'Local Fish Market Visit',
    'The early morning fish market near the main jetty is active from around 6 AM. Freshly caught fish, squid, and shellfish are brought in directly from overnight fishing boats. Interesting for photography and to purchase fresh seafood to have cooked at a local restaurant.',
    'SHOPPING',
    0.00, 500.00, 60, 6
FROM destinations WHERE code = 'SAINT_MARTIN';

INSERT INTO destination_activities (
    destination_id, title, description, category,
    estimated_cost_min, estimated_cost_max, recommended_duration_minutes, priority
)
SELECT
    id,
    'Photography Walk',
    'Saint Martin offers diverse photography subjects: coral formations, fishing boats, local village life, coconut trees, and the open sea. The northern jetty area at golden hour and the eastern rocky shore at low tide are especially popular spots.',
    'ACTIVITY',
    0.00, 0.00, 120, 6
FROM destinations WHERE code = 'SAINT_MARTIN';
