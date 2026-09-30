# 0001. Pile technique du socle et versions LTS

Statut : acceptée.

## Contexte

Le dépôt sert à la fois de démonstrateur et de socle pour des projets clients. Sa pile est en
avance de phase sur trois points, assumés pour un démonstrateur, à trancher pour un projet livré :

- JDK 26 (`.sdkmanrc`, `backend/pom.xml`, `deploy/backend/Dockerfile`, workflows) n'est pas une
  version LTS : son support s'arrête six mois après sa sortie ;
- MapStruct `1.7.0.Beta2` est le processeur d'annotations qui génère les mappers : une bêta
  conditionne du code exécuté en production ;
- le dépôt de snapshots Spring est déclaré dans `backend/pom.xml` (releases désactivées, aucune
  dépendance snapshot externe aujourd'hui).

Springdoc est déjà cassé sur cette combinaison, ce que la configuration de la gateway documente
et contourne (`SpringDocAuthenticationConfiguration`).

## Options

1. Aligner le démonstrateur lui-même sur une pile LTS. Coût : perte de la veille technologique
   que le démonstrateur assure, et Boot 4.1 impose de toute façon Java 17 minimum, pas plus.
2. Conserver la pile actuelle sur le démonstrateur et fixer une cible pour les projets dérivés :
   JDK LTS le plus récent (21 aujourd'hui, 25 dès qu'un socle le supporte), MapStruct en version
   finale, pas de dépôt de snapshots. Un projet dérivé commence par cette mise au niveau.
3. Ne rien fixer, décider projet par projet.

## Décision

Option 3 : rien n'est fixé au niveau du socle. Chaque projet dérivé décide de sa pile au
démarrage, avec cette fiche comme liste des trois points à examiner (version de Java, MapStruct,
dépôt de snapshots). Le démonstrateur garde sa pile en avance de phase.

## Conséquences

Option 2 : le démonstrateur continue d'anticiper, chaque projet dérivé démarre par une baisse de
version de Java (compilation à vérifier : `java.version` dans le pom, image de base du Dockerfile,
`.sdkmanrc`, `setup-java` des workflows) et un passage de MapStruct en version finale.
