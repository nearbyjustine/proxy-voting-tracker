-- Synthetic demo data. Org codes match the "org" attribute of the Keycloak users.
INSERT INTO organization (code, name) VALUES ('STEWARD', 'Stewardship Pension Fund'), ('GROWTH', 'Growth Capital Partners');

INSERT INTO company (ticker, name, country) VALUES
 ('ORCH', 'Orchard Foods', 'GB'),
 ('PSDN', 'Poseidon Shipping', 'SG'),
 ('LUMA', 'Luma Health', 'US'),
 ('BAYN', 'Bayani Telecom', 'PH');

-- Deadlines are relative to "now" so the demo always has open, closing-soon and closed meetings.
INSERT INTO meeting (external_id, company_id, meeting_date, vote_deadline, market_time_zone, meeting_type, updated_at) VALUES
 ('ORCH-AGM', (SELECT id FROM company WHERE ticker='ORCH'), (now() + interval '12 days')::date, now() + interval '9 days',  'Europe/London',    'AGM', now()),
 ('PSDN-AGM', (SELECT id FROM company WHERE ticker='PSDN'), (now() + interval '3 days')::date,  now() + interval '30 hours', 'Asia/Singapore',  'AGM', now()),
 ('LUMA-EGM', (SELECT id FROM company WHERE ticker='LUMA'), (now() + interval '20 days')::date, now() + interval '17 days', 'America/New_York', 'EGM', now()),
 ('BAYN-AGM', (SELECT id FROM company WHERE ticker='BAYN'), (now() - interval '2 days')::date,  now() - interval '4 days',  'Asia/Manila',      'AGM', now());

INSERT INTO proposal (meeting_id, seq, category, title, description, board_recommendation, pay_score, board_independence_pct) VALUES
 ((SELECT id FROM meeting WHERE external_id='ORCH-AGM'), 1, 'DIRECTOR_ELECTION', 'Re-elect Oliver Grant as chair', 'Chair since 2014; also serves as former CEO. The board has 4 of 9 independent members.', 'FOR', NULL, 44),
 ((SELECT id FROM meeting WHERE external_id='ORCH-AGM'), 2, 'SAY_ON_PAY', 'Approve the directors'' remuneration report', 'CEO pay increased 12% in line with a 15% rise in operating profit. Long-term incentives vest over five years with a two-year holding period.', 'FOR', 78, NULL),
 ((SELECT id FROM meeting WHERE external_id='ORCH-AGM'), 3, 'AUDITOR', 'Reappoint the external auditor', 'The audit firm has served for 6 years; non-audit fees are 9% of audit fees.', 'FOR', NULL, NULL),
 ((SELECT id FROM meeting WHERE external_id='PSDN-AGM'), 1, 'SAY_ON_PAY', 'Advisory vote on executive pay', 'The CEO received a one-off retention award of 3x salary with no performance conditions, during a year in which the dividend was cut.', 'FOR', 31, NULL),
 ((SELECT id FROM meeting WHERE external_id='PSDN-AGM'), 2, 'SHAREHOLDER_ENV', 'Shareholder proposal: adopt a fleet decarbonisation plan', 'Requests a plan aligned with IMO 2050 targets with interim milestones and annual reporting.', 'AGAINST', NULL, NULL),
 ((SELECT id FROM meeting WHERE external_id='LUMA-EGM'), 1, 'MERGER', 'Approve acquisition of CareLink Inc.', 'Cash-and-stock deal valuing CareLink at 14x EBITDA. Expected to be accretive to earnings in year two.', 'FOR', NULL, NULL),
 ((SELECT id FROM meeting WHERE external_id='BAYN-AGM'), 1, 'DIRECTOR_ELECTION', 'Elect Andrea Santos as independent director', 'Former regulator with telecom policy experience.', 'FOR', NULL, 67);

INSERT INTO voting_policy (organization_id, name, updated_at, updated_by) VALUES
 ((SELECT id FROM organization WHERE code='STEWARD'), 'Stewardship policy 2026', now(), 'seed'),
 ((SELECT id FROM organization WHERE code='GROWTH'),  'Growth house view',       now(), 'seed');

INSERT INTO policy_rule (policy_id, priority, category, condition_type, threshold, decision, rationale) VALUES
 ((SELECT id FROM voting_policy WHERE name='Stewardship policy 2026'), 1, 'SAY_ON_PAY',        'PAY_SCORE_BELOW',          50, 'AGAINST', 'Pay is poorly aligned with performance'),
 ((SELECT id FROM voting_policy WHERE name='Stewardship policy 2026'), 2, 'DIRECTOR_ELECTION', 'BOARD_INDEPENDENCE_BELOW', 50, 'AGAINST', 'Board lacks a majority of independent directors'),
 ((SELECT id FROM voting_policy WHERE name='Stewardship policy 2026'), 3, 'SHAREHOLDER_ENV',   NULL,                       NULL, 'FOR',    'Supports better climate disclosure'),
 ((SELECT id FROM voting_policy WHERE name='Growth house view'),      1, 'MERGER',            NULL,                       NULL, 'FOR',    'Supports value-accretive consolidation'),
 ((SELECT id FROM voting_policy WHERE name='Growth house view'),      2, 'SAY_ON_PAY',        'PAY_SCORE_BELOW',          30, 'AGAINST', 'Only opposes egregious pay');
