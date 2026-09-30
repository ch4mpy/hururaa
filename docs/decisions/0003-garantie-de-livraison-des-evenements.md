# 0003. Garantie de livraison des événements

Statut : acceptée.

## Contexte

`hururaa-api` publie un `ResourceEvent` après commit, la gateway le relaie en SSE, le frontend
recharge la ressource par REST. La chaîne n'offre aucune garantie : pas d'outbox (un crash entre
le commit et l'`afterCommit` perd l'événement), échec broker avalé avec un 2xx rendu à l'appelant,
`publisher-confirm-type` non configuré, file `AnonymousQueue` non durable côté gateway (tout
événement publié pendant un redémarrage est perdu), aucun identifiant d'événement (ni
déduplication ni ordonnancement), `@RabbitListener` sans retry ni DLQ.

Pour un flux de notification dont le frontend refetch systématiquement, c'est défendable, mais ce
n'est écrit nulle part et rien ne détecte la dérive.

## Options

1. Assumer le « au mieux » : l'écrire dans `CLAUDE.md` et dans la Javadoc de
   `ResourceEventPublisher`, et ajouter une métrique de publications échouées pour le voir.
2. Fiabiliser le transport sans changer le modèle : `publisher-confirm-type: correlated`, file
   nommée et durable côté gateway avec TTL et longueur bornés, identifiant d'événement (UUID)
   dans `ResourceEvent`, DLQ sur le listener. Une journée. Ne couvre pas le crash entre commit
   et publication.
3. Outbox transactionnel : l'événement est écrit dans la même transaction que la ressource,
   un relais le publie. Couvre tout, deux à trois jours, et une table de plus à administrer.

## Décision

Option 1 : la livraison est « au mieux », et c'est écrit dans `CLAUDE.md` et dans la Javadoc
de `ResourceEventPublisher`. Un compteur Micrometer `hururaa.events.publish.failures` rend les
échecs de publication visibles dans Grafana. Rien dans l'application ne doit dépendre de la
réception d'un événement : le frontend recharge par REST.

## Conséquences

Option 1 : aucune, sinon une phrase de documentation et une métrique. Les options 2 et 3
modifient `common-events-starter`, partagé par les deux applications.
