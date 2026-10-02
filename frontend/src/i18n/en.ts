export default {
  app: { title: 'Proxy Voting', signIn: 'Sign in', signOut: 'Sign out', language: 'Language' },
  nav: { home: 'Home', meetings: 'Meetings', policy: 'Voting policy', audit: 'Audit log', ingest: 'Import' },
  home: {
    welcome: 'Welcome, {name}', org: 'Organisation: {org}',
    intro: 'Track shareholder meetings, get recommendations from your own voting policy, and vote before the deadline.',
    signedOut: 'Demo users: sam / sam123 (Stewardship fund: all roles), vic / vic123 (Stewardship: voter), gina / gina123 (Growth Capital).',
  },
  common: { save: 'Save', cancel: 'Cancel', loading: 'Loading…', none: 'Nothing here yet.', error: 'Error', ref: 'Reference: {id}', prev: 'Previous', next: 'Next', page: 'Page {n} of {total}' },
  meetings: {
    title: 'Meetings', company: 'Company', market: 'Market', deadline: 'Vote deadline', status: 'Status', progress: 'Voted', open: 'Open',
    yourTime: 'your time', marketTime: 'market time', board: 'Board', recommendation: 'Our policy', yourVote: 'Your organisation\'s vote',
    summarize: 'Summarize', summary: 'Summary', payScore: 'Pay score', independence: 'Board independence', votedBy: 'by {user}',
    closedNote: 'Voting has closed for this meeting.', againstPolicy: 'Differs from policy',
  },
  status: { OPEN: 'Open', CLOSING_SOON: 'Closing soon', CLOSED: 'Closed' },
  decision: { FOR: 'For', AGAINST: 'Against', ABSTAIN: 'Abstain' },
  category: { DIRECTOR_ELECTION: 'Director election', SAY_ON_PAY: 'Say on pay', AUDITOR: 'Auditor', MERGER: 'Merger', SHAREHOLDER_ENV: 'Shareholder – environment', SHAREHOLDER_SOCIAL: 'Shareholder – social', OTHER: 'Other' },
  condition: { none: '(no condition)', PAY_SCORE_BELOW: 'pay score below', BOARD_INDEPENDENCE_BELOW: 'board independence % below', BOARD_RECOMMENDS_AGAINST: 'board recommends against' },
  policy: {
    title: 'Voting policy', name: 'Policy name', rules: 'Rules (first match wins)', anyCategory: 'Any category', addRule: 'Add rule',
    recalculate: 'Recalculate recommendations', saved: 'Saved. {n} recommendations updated.', recalculated: '{n} recommendations recalculated.',
    when: 'When', and: 'and', then: 'then', because: 'because', updated: 'Last changed by {by}', fallback: 'If no rule matches: follow the board.',
  },
  audit: { title: 'Audit log', when: 'When', who: 'Who', what: 'Action', details: 'Details' },
  ingest: { title: 'Import meetings', help: 'Upload a CSV in the standard format. It goes straight to S3; a Lambda splits it into meetings and they appear here within seconds.', upload: 'Upload', done: 'Uploaded {key}. Processing…', sample: 'See sample-data/meetings.csv for the format.' },
}
