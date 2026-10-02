export default {
  app: { title: 'Vote par procuration', signIn: 'Se connecter', signOut: 'Se déconnecter', language: 'Langue' },
  nav: { home: 'Accueil', meetings: 'Assemblées', policy: 'Politique de vote', audit: 'Journal d\'audit', ingest: 'Import' },
  home: {
    welcome: 'Bienvenue, {name}', org: 'Organisation : {org}',
    intro: 'Suivez les assemblées d\'actionnaires, obtenez des recommandations selon votre propre politique et votez avant l\'échéance.',
    signedOut: 'Utilisateurs de démo : sam / sam123, vic / vic123, gina / gina123.',
  },
  common: { save: 'Enregistrer', cancel: 'Annuler', loading: 'Chargement…', none: 'Rien pour le moment.', error: 'Erreur', ref: 'Référence : {id}', prev: 'Précédent', next: 'Suivant', page: 'Page {n} sur {total}' },
  meetings: {
    title: 'Assemblées', company: 'Société', market: 'Marché', deadline: 'Date limite de vote', status: 'Statut', progress: 'Voté', open: 'Ouvrir',
    yourTime: 'votre heure', marketTime: 'heure du marché', board: 'Conseil', recommendation: 'Notre politique', yourVote: 'Vote de votre organisation',
    summarize: 'Résumer', summary: 'Résumé', payScore: 'Score de rémunération', independence: 'Indépendance du conseil', votedBy: 'par {user}',
    closedNote: 'Le vote est clos pour cette assemblée.', againstPolicy: 'Diffère de la politique',
  },
  status: { OPEN: 'Ouvert', CLOSING_SOON: 'Bientôt clos', CLOSED: 'Clos' },
  decision: { FOR: 'Pour', AGAINST: 'Contre', ABSTAIN: 'Abstention' },
  category: { DIRECTOR_ELECTION: 'Élection d\'administrateur', SAY_ON_PAY: 'Vote sur la rémunération', AUDITOR: 'Commissaire aux comptes', MERGER: 'Fusion', SHAREHOLDER_ENV: 'Actionnaires – environnement', SHAREHOLDER_SOCIAL: 'Actionnaires – social', OTHER: 'Autre' },
  condition: { none: '(aucune condition)', PAY_SCORE_BELOW: 'score de rémunération inférieur à', BOARD_INDEPENDENCE_BELOW: 'indépendance du conseil (%) inférieure à', BOARD_RECOMMENDS_AGAINST: 'le conseil recommande contre' },
  policy: {
    title: 'Politique de vote', name: 'Nom', rules: 'Règles (la première qui s\'applique gagne)', anyCategory: 'Toute catégorie', addRule: 'Ajouter une règle',
    recalculate: 'Recalculer les recommandations', saved: 'Enregistré. {n} recommandations mises à jour.', recalculated: '{n} recommandations recalculées.',
    when: 'Si', and: 'et', then: 'alors', because: 'car', updated: 'Modifiée par {by}', fallback: 'Si aucune règle ne s\'applique : suivre le conseil.',
  },
  audit: { title: 'Journal d\'audit', when: 'Quand', who: 'Qui', what: 'Action', details: 'Détails' },
  ingest: { title: 'Importer des assemblées', help: 'Téléversez un CSV au format standard. Il va directement sur S3 ; une Lambda le découpe en assemblées.', upload: 'Téléverser', done: '{key} téléversé. Traitement…', sample: 'Voir sample-data/meetings.csv pour le format.' },
}
