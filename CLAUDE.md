# Playtime Tracker — Mod Minecraft Forge

## Objet du projet

Mod Minecraft **client-side** qui mesure le temps passé sur chaque serveur multijoueur et
chaque monde solo, en **distinguant le temps réellement joué du temps AFK**.
Les données sont consultables via un écran greffé sur la GUI Statistiques du jeu.

---

## 1. Environnement technique

| Élément | Valeur | Note |
|---|---|---|
| Minecraft | **1.12.2** | Version de référence de la branche 1.12 |
| Forge (compilation) | **14.23.5.2847** | Voir l'encadré ci-dessous — ce n'est pas le recommended build |
| Forge (exécution) | 2847 et au-delà, dont le recommended **2860** | |
| Mappings MCP | **snapshot_20171003** | `stable_39` est déclaré pour 1.12 et déclenche un avertissement en 1.12.2 |
| ForgeGradle | **2.3.10** (version publiée, pinnée) | Préférée à `2.3-SNAPSHOT` pour un build reproductible |
| Gradle | **4.10.3** (wrapper) | ForgeGradle 2.3 ne fonctionne pas au-delà |
| JDK de build | **JDK 8 obligatoire** | `C:\Program Files\Eclipse Adoptium\jdk-8.0.472.8-hotspot` (déjà en `JAVA_HOME`) |
| `sourceCompatibility` | 1.8 | |

> ⚠️ **Pourquoi 2847 et pas le recommended build 2860 ?**
> L'artefact `forge-<version>-userdev.jar`, indispensable à ForgeGradle 2.3 pour compiler,
> **n'est pas publié** sur `maven.minecraftforge.net` pour les builds 2848 à 2860.
> Vérifié en août 2026 : 2838 et 2847 renvoient HTTP 200, 2848 à 2860 renvoient tous 404.
> **2847 est donc le build le plus récent compilable.** Ce n'est pas une limitation
> fonctionnelle : l'API Forge 1.12.2 est stable sur cette plage, et le mod compilé contre
> 2847 tourne sans modification sur 2860.
> Ne pas « corriger » `forgeVersion` vers 2860 : le build échouerait sur
> `Could not find forge-userdev.jar`.

> ⚠️ **Ne jamais compiler avec le JDK 17/25 présent sur la machine.** ForgeGradle 2.3
> et Gradle 4.x plantent sur tout JDK > 8. Si le build échoue avec des erreurs de type
> `Unsupported class file major version` ou `NoClassDefFoundError` dans Gradle,
> le premier réflexe est de vérifier `JAVA_HOME`.

### Identité du mod

- **modid** : `playtimetracker`
- **Mod name** : `Playtime Tracker`
- **Package racine** : `fr.julien.playtimetracker`
- **Version** : SemVer, jar nommé `playtimetracker-1.12.2-0.1.0.jar`

---

## 2. Décisions fonctionnelles (validées avec l'utilisateur)

### 2.1 Côté d'exécution

**Client uniquement.** Le mod n'a pas besoin d'être installé sur le serveur et
fonctionne sur n'importe quel serveur vanilla ou moddé.
→ Conséquence : `clientSideOnly = true` dans `@Mod`, aucune classe serveur, aucun packet réseau.

### 2.2 Détection d'activité

Le compteur est considéré **actif** si au moins un de ces signaux survient :

1. **Intention de déplacement** — lecture de `MovementInput` (avancer / reculer / strafe /
   saut / sneak), et **non** de la position résultante.
2. **Rotation caméra** (variation de `yaw` / `pitch`)
3. **Entrées clavier/souris** (touche pressée, clic, molette)
4. **Interactions de gameplay** (casser/poser un bloc, ouvrir un inventaire, envoyer un
   message dans le chat, crafter)

> **Pourquoi l'intention et pas la position ?** La position d'un joueur varie en permanence
> sans action de sa part : gravité, courant d'eau, minecart, bateau, monture, knockback,
> repositionnement serveur (rubber-banding), imprécision flottante. Or les fermes AFK
> classiques reposent précisément sur ce déplacement passif. Mesurer la position
> classerait « actif » exactement les situations que le mod doit détecter comme AFK.
> `MovementInput` est nul quand le joueur est porté : signal binaire, aucun seuil arbitraire
> à calibrer, et trivial à porter vers les versions récentes de Minecraft.

**États spéciaux** (écran de mort en attente de respawn, écrans de chargement, transitions
de dimension) : **aucune règle particulière**, ils sont traités comme n'importe quelle
inactivité et soumis au seuil normal. Moins de cas particuliers = moins de bugs.

### 2.3 Règle AFK

- Seuil : **5 minutes** par défaut, **configurable**.
- Quand le seuil est atteint : le compteur actif s'arrête **et les 5 minutes déjà
  comptabilisées sont retirées de l'actif et transférées dans le compteur AFK**
  (retrait rétroactif — c'est le point central du mod).
- Toute activité relance immédiatement le compteur actif.
- **Fenêtre du jeu non focus (alt-tab) → AFK immédiat**, sans attendre le seuil, avec
  retrait rétroactif du temps d'inactivité déjà écoulé. Idem pour le menu pause en solo.

### 2.4 Granularité et clés de données

- **Par serveur multijoueur**, clé = `hôte:port` (une entrée par réseau ; pas de tentative
  de distinguer les sous-serveurs BungeeCord). Le nom que le joueur a donné au serveur dans
  sa liste est stocké comme libellé d'affichage.
- **Par monde solo**, clé = nom du dossier de sauvegarde (et non le nom affiché, qui peut
  être renommé sans que l'historique doive se scinder en deux).
- **Realms n'est volontairement pas tracké.** `getCurrentServerData()` renvoie `null` pour
  une connexion Realms : aucune clé stable n'est disponible côté client. Décision validée
  avec l'utilisateur — comportement attendu, pas un bug à corriger.
- **Par compte Minecraft**, clé = **UUID** du joueur (résiste aux changements de pseudo).

### 2.5 Résistance au crash — session provisoire

Deux règles actées se contredisaient : « une session n'est validée qu'à sa clôture » et
« perte maximale sur crash : 60 s ». Avec un commit uniquement à la clôture, un crash après
trois heures perdait les trois heures.

**Résolution** : chaque autosave écrit aussi la session en cours comme entrée
**provisoire** (`ProvisionalSession`, champ `inProgress` du JSON). Elle est effacée à toute
clôture propre, et n'est relue au démarrage suivant **que si le jeu n'est pas sorti
proprement**. Le retrait rétroactif ayant déjà été appliqué par le moteur au moment de
l'instantané, une session récupérée est comptabilisée exactement comme une session close.
La règle des 30 s s'applique aussi à la récupération.

### 2.6 Cycle de vie des sessions

**Sessions courtes ignorées.** Une session de **moins de 30 secondes est intégralement
jetée** : elle n'entre ni dans l'historique, ni dans les totaux de la cible. Un serveur sur
lequel on n'a fait que des allers-retours de quelques secondes n'apparaît donc pas du tout
dans la liste — c'est ce qui garde l'écran lisible.
→ Conséquence d'implémentation : une session n'est **validée qu'à sa clôture**, jamais
incrémentalement, sinon on ne pourrait pas l'annuler rétroactivement.

**Rétention.** Les sessions détaillées sont conservées **90 jours**. Au-delà, elles sont
**compactées en agrégats mensuels** par cible (temps actif, temps AFK, nombre de sessions).
Aucun temps n'est jamais perdu dans les totaux : seul le détail session par session
disparaît. Le fichier de données reste ainsi de taille bornée dans le temps.
Le compactage tourne au démarrage du jeu.

### 2.7 Persistance

- Format : **JSON unique**, lisible et éditable à la main, en pretty-print.
- Emplacement : `.minecraft/config/playtimetracker/`
- **Autosave périodique** (60 s par défaut, configurable) + à la déconnexion + à la fermeture.
- **Écriture atomique** : fichier `.tmp` dans le même dossier, `FileChannel.force()` pour
  vider le cache de l'OS, puis `ATOMIC_MOVE`. Le fichier cible est toujours soit l'ancienne
  version complète, soit la nouvelle — jamais un mélange tronqué.
- Perte maximale sur crash : l'intervalle d'autosave.

**Sérialisation écrite à la main**, sans réflexion Gson. Trois raisons : le JSON reste un
contrat stable et documenté que le joueur peut relire ; renommer un champ Java ne casse pas
silencieusement les fichiers existants ; rien ne dépend de la réflexion survivant à
l'obfuscation du jar publié.

**Tolérance aux fichiers abîmés.** Une entrée illisible (session incohérente, clé de cible
non parsable, mois malformé) est ignorée, pas fatale : un dégât partiel doit coûter les
entrées abîmées, pas tout l'historique. Un fichier globalement illisible est **déplacé en
quarantaine** (`.corrupt-<horodatage>`) et le mod repart à vide — jamais supprimé.

**Fichier écrit par une version plus récente du mod** (`schemaVersion` supérieur) :
lecture refusée via `UnsupportedSchemaException`, fichier laissé intact. L'écraser
détruirait des champs inconnus de cette version.

**Totaux dérivés, jamais stockés.** Chaque total est recalculé depuis les sessions et les
agrégats. Un compteur stocké pourrait diverger de ce qu'il résume ; dériver rend
l'invariant « le compactage ne change jamais les totaux » vrai par construction plutôt que
par discipline.

### 2.8 Interface — le contexte présent, pas un catalogue

**Écran unique greffé sur la GUI Statistiques vanilla**, qui affiche **uniquement la
destination où le joueur se trouve** : ce serveur, ou ce monde. Pas de liste, pas de
navigation, pas de clic.

> **Décision révisée le 2026-08-29.** La première version listait toutes les destinations
> avec un écran de détail par clic. À l'usage c'était un catalogue, et un catalogue répond
> à une question qu'on ne se pose pas en jouant. Ce qu'on veut savoir manette en main, c'est
> « depuis combien de temps je joue *ici* ».

**Le stockage reste inchangé** : chaque serveur et chaque monde continuent d'être
enregistrés séparément, et l'historique d'une destination attend le joueur quand il y
revient. Seul l'affichage se restreint au contexte courant.

Contenu, en trois blocs séparés par des filets :
- **Session en cours** — état actif/AFK en couleur, temps joué, temps AFK, en direct
- **Total ici** — cumul joué / AFK / pourcentage réellement joué sur cette destination
- **Détails** — première fois, nombre de sessions, durée moyenne, session la plus longue

**Les totaux incluent la session en cours.** Un joueur pense « mon temps sur ce serveur »
comme incluant l'instant présent ; n'afficher que les sessions closes ferait paraître
l'écran périmé à la seconde où il l'ouvre.

**L'historique daté des sessions n'est pas affiché** (décision du 2026-08-29). Les données
sont toujours enregistrées : on pourra le réafficher sans rien changer au stockage.

**Aucun feedback en jeu** lors des bascules actif ↔ AFK : le mod est totalement silencieux
(pas de HUD, pas de message de chat). Un log de diagnostic existe, désactivé par défaut.

---

## 3. Architecture

Objectif : **rendre le portage vers d'autres versions de Minecraft peu coûteux.**

```
1.12/
├─ core/                        Module Gradle — JAVA PUR, ZÉRO import Minecraft
│  ├─ src/main/java/fr/julien/playtimetracker/core/
│  │  ├─ PlaytimeTracker.java   Façade : cycle de vie, autosave, récupération
│  │  ├─ model/                 TargetType, TargetKey, TrackedSession,
│  │  │                         TrackedTarget, PlayerPlaytime, PlaytimeData,
│  │  │                         MonthlyAggregate, ProvisionalSession
│  │  ├─ engine/                Clock, SystemClock, ActivityState,
│  │  │                         SessionSnapshot, PlaytimeEngine, RetentionPolicy
│  │  ├─ storage/               PlaytimeRepository, JsonPlaytimeStore, PlaytimeCodec,
│  │  │                         AtomicFileWriter, UnsupportedSchemaException
│  │  ├─ util/                  DurationFormatter, DateFormatter
│  │  └─ config/                PlaytimeConfig (POJO)
│  └─ src/test/java/            Tests JUnit du moteur (horloge injectée)
│
└─ forge-1.12/                  Module Gradle — couche d'adaptation Forge 1.12.2
   └─ src/main/java/fr/julien/playtimetracker/forge/
      ├─ PlaytimeTrackerMod.java     @Mod, cycle de vie
      ├─ Reference.java              modid, nom, version (substituée au build)
      ├─ bridge/                     TargetResolver, TargetIdentity
      ├─ event/                      PlaytimeClientHandler, StatsGuiHandler
      ├─ client/gui/                 GuiPlaytimeStats
      └─ config/                     ForgeConfig, PlaytimeGuiFactory,
                                     ConfigChangeHandler
```

### Règles d'architecture non négociables

1. **`core` n'importe JAMAIS une classe `net.minecraft.*` ou `net.minecraftforge.*`.**
   C'est la garantie du portage multi-version. Un import Minecraft dans `core` est un bug.
2. Le temps est **injecté** dans le moteur (interface `Clock`), jamais lu depuis
   `System.currentTimeMillis()` à l'intérieur de la logique métier → tests déterministes
   (on simule 5 minutes d'AFK instantanément).
3. Le moteur raisonne en **millisecondes**, pas en ticks : les ticks varient avec le lag
   serveur et fausseraient les mesures.
4. La couche Forge ne fait que **traduire** : capter des événements Minecraft et appeler
   `engine.onActivity(...)` / `engine.tick(now)`. Aucune règle métier dans `forge-1.12`.

---

## 4. Feuille de route

Construction **MVP puis itérations**, avec validation en jeu à chaque étape.

- [x] **Étape 1 — Squelette** : structure Gradle multi-module, `build.gradle` ForgeGradle 2.3,
      `mcmod.info`, classe `@Mod` minimale.
      *Fait : `gradlew build` produit `playtimetracker-1.12.2-0.1.0.jar`, classes `core`
      embarquées, version substituée, tests JUnit verts.*
- [x] **Étape 2 — Moteur** : `PlaytimeEngine` avec le retrait rétroactif, la bascule AFK
      immédiate sur perte de focus et l'abandon des sessions courtes.
      *Fait : 23 tests JUnit verts, zéro import Minecraft dans `core`. Validé par test de
      mutation — neutraliser le retrait rétroactif fait tomber 8 tests.*
- [x] **Étape 3 — Persistance** : modèle de données par UUID × cible, codec JSON explicite,
      écriture atomique, quarantaine des fichiers corrompus, compactage mensuel.
      *Fait : 54 tests JUnit verts au total. Validé par test de mutation — un compactage qui
      perd des données fait tomber 5 tests.*
- [x] **Étape 4 — Intégration Forge** : capture des signaux d'activité, détection du focus
      fenêtre, résolution de la cible, branchement du cycle de vie, config Forge.
      *Fait : 66 tests JUnit verts, mod actif en jeu, config générée.*
      *Reste à valider manuellement en jeu : voir §8.*
- [x] **Étape 5 — GUI** : bouton greffé sur l'écran Statistiques vanilla, écran principal
      avec session en direct, totaux globaux et liste scrollable triée.
      *Fait : 73 tests JUnit verts, écran compilé et chargé en jeu.*
- [x] **Étape 6 — Détail & finitions** : écran de détail par serveur (clic sur une ligne),
      historique unifié sessions + mois compactés, config éditable in-game, i18n FR/EN.
      *Fait : 79 tests JUnit verts, chargé en jeu sans erreur.*

---

## 5. Commandes

```bash
# Toujours depuis la racine du projet, avec JAVA_HOME sur le JDK 8
./gradlew setupDecompWorkspace   # première fois uniquement (long)
./gradlew build                  # compile + produit le jar
./gradlew runClient              # lance Minecraft avec le mod
./gradlew :core:test             # tests unitaires du moteur
```

---

## 6. Conventions

- **Langue** : échanges avec l'utilisateur en **français**. Code, identifiants et commentaires
  techniques en **anglais** (convention Minecraft/Forge).
- **i18n** : aucun texte affiché en dur dans le code, tout passe par les fichiers `.lang`
  (`fr_fr.lang`, `en_us.lang`).
- Ne jamais utiliser de noms obfusqués (`func_xxxxx_x`) : toujours les noms MCP mappés.
- Ce fichier est mis à jour **au fil des décisions** : toute nouvelle décision technique ou
  fonctionnelle validée avec l'utilisateur doit y être consignée.

---

## 7. Pièges rencontrés (ne pas les redécouvrir)

- **`Could not find forge-userdev.jar`** → `forgeVersion` pointe sur un build sans artefact
  userdev publié. Voir l'encadré de la section 1 : rester sur 14.23.5.2847.
- **`Could not find net.minecraftforge:forge:1.12.2-null`** → à l'intérieur du bloc
  `minecraft { }`, le delegate Groovy est l'extension ForgeGradle, qui possède ses propres
  propriétés `mcVersion` et `forgeVersion`. Une référence non qualifiée y résout sur
  l'extension (vide), pas sur `gradle.properties`. Toujours qualifier avec `project.`,
  ou résoudre la valeur à l'extérieur du bloc (c'est ce que fait `forge-1.12/build.gradle`).
- **Avertissement `This mapping 'stable_39' was designed for MC 1.12`** → utiliser
  `snapshot_20171003`, le mapping de référence pour 1.12.2.


---

## 8. À valider manuellement en jeu

L'intégration Forge ne peut pas être couverte par des tests automatisés. Points à vérifier
lors d'une session réelle :

- [x] Session solo détectée : clé `singleplayer:New World` = nom du dossier de sauvegarde
      (vérifié le 2026-08-29)
- [x] Écran de statistiques vérifié visuellement le 2026-08-29 : bouton présent, session en
      direct, totaux, ligne triée avec ratio coloré.
- [ ] Écran de détail (clic sur une ligne) — pas encore vérifié visuellement
- [ ] Rejoindre un serveur → clé `server:hôte:port`, libellé = nom dans la liste de serveurs
- [x] **Inactivité → AFK avec retrait rétroactif** (vérifié le 2026-08-29, seuil abaissé à
      60 s pour le test) : 25 s de jeu puis 60 s d'immobilité → bascule AFK à la seconde
      près, `joué` inchangé, `afk` +60 s. Aucune seconde perdue ni comptée deux fois.
- [x] **Alt-tab → AFK immédiat** (vérifié le 2026-08-29) : bascule instantanée dans les
      deux sens, avec un retrait rétroactif nul quand le joueur était actif jusqu'au
      basculement. Vérifié sur 5 allers-retours consécutifs, comptabilité cohérente.
- [x] Menu pause en solo → bascule AFK immédiate (vérifié le 2026-08-29)
- [x] **Inventaire → reste actif** (vérifié le 2026-08-29) : 122 s consécutives de
      manipulation d'inventaire, intégralement comptées comme jouées, aucune bascule AFK.
- [x] **Courant d'eau → AFK** (vérifié le 2026-08-29) — **le test décisif.** Joueur
      effectivement déplacé par le courant, sans aucune entrée pendant plus d'une minute :
      bascule AFK au seuil, `joué` inchangé. Le choix de lire `MovementInput` plutôt que la
      position est validé en conditions réelles : un compteur basé sur le déplacement aurait
      compté ce temps comme joué.
- [x] Quitter le jeu par la croix → le hook de fermeture s'exécute et écrit le fichier
      (vérifié le 2026-08-29). Confirmé avec une session réellement ouverte : `inProgress`
      absent du JSON après fermeture, session close et enregistrée.
- [x] **Crash → récupération** (vérifié le 2026-08-29) : processus tué via `Stop-Process
      -Force`, donc sans aucun hook de fermeture. Au relancement,
      `Recovered 11 minutes of play from a session the last run did not close.`
      `inProgress` effacé, session déplacée dans `sessions` avec les valeurs exactes du
      dernier autosave. Perte : l'intervalle d'autosave, rien de plus.

Realms n'apparaît pas dans cette liste : son absence de suivi est un choix assumé (§2.4).

### ⚠️ Piège de l'environnement de développement : le pseudo change à chaque lancement

`gradlew runClient` démarre Minecraft avec un pseudo aléatoire (`Player640`, `Player123`…),
visible dans le log via `Setting user:`. L'UUID hors-ligne étant dérivé du pseudo,
**chaque lancement crée un compte différent** et les données du lancement précédent
n'apparaissent plus dans l'écran de statistiques.

Ce n'est **pas** un bug : sur une installation réelle, l'UUID du compte Mojang est stable.
Mais pour tester la GUI, il faut soit rester dans un seul lancement, soit forcer un pseudo
fixe. Constaté le 2026-08-29 : deux sessions consécutives enregistrées sous
`f2ba1573-…` puis `dfa57433-…`.

---

## 9. Notes d'implémentation GUI

- **Le bouton est ajouté à `GuiStats` via `GuiScreenEvent.InitGuiEvent.Post`**, avec un id
  volontairement éloigné (7913) de ceux que vanilla utilise pour ses propres boutons, et
  l'événement d'action est annulé pour que l'écran vanilla ne réagisse pas à un id inconnu.
- **`GuiPlaytimeStats.doesGuiPauseGame()` renvoie `false`** : ouvrir les statistiques ne
  doit pas modifier ce que le mod est en train de mesurer.
- **La liste est construite à l'ouverture de l'écran, pas à chaque frame.** Les totaux ne
  changent qu'à la clôture d'une session ; retrier la liste 60 fois par seconde serait du
  gaspillage pur.
- **Durées formatées avec des lettres d'unité** (`5h 12m`) et non des mots : elles se lisent
  identiquement en français et en anglais, donc les chiffres n'ont pas besoin d'être
  traduits et les colonnes restent étroites.
- **Code couleur du ratio** : vert au-dessus de 80 % de temps joué, rouge en dessous de
  40 %. Un coup d'œil suffit à repérer les serveurs où l'on est surtout AFK.
- **Dates au format année en premier** (`2026-08-29`) et non dans un ordre localisé :
  `08/09/2026` désigne deux jours différents selon le lecteur, et le mod a des utilisateurs
  français et anglais qui regardent les mêmes chiffres.
- **L'historique du détail mêle sessions et mois compactés dans une seule liste**, les mois
  étant grisés. Du point de vue du joueur c'est une seule chronologie ; le fait que les
  entrées anciennes aient perdu leur détail est une préoccupation d'implémentation, pas un
  critère d'organisation de l'écran.
- **Config éditable en jeu** via `IModGuiFactory` (bouton « Config » de la liste des mods).
  `ForgeConfig` conserve son instance `Configuration` pour que l'écran édite le même objet
  et non une copie divergente. Tous les réglages s'appliquent sans redémarrage : le moteur
  relit le seuil AFK à chaque évaluation au lieu de le mettre en cache.

### Bugs d'affichage trouvés en test (corrigés le 2026-08-29)

- **`Format error: %` en en-tête de colonne.** Minecraft passe toute traduction par
  `String.format`, donc un `%` isolé dans un fichier `.lang` n'est pas un spécificateur
  valide. Corrigé en évitant le caractère (`Ratio`). À retenir pour toute nouvelle clé.
- **Mojibake `3Œ` sur un préfixe d'icône.** Gradle compilait avec l'encodage par défaut de
  la plateforme (windows-1252). Corrigé par `options.encoding = 'UTF-8'` sur toutes les
  tâches `JavaCompile` dans le `build.gradle` racine. Le préfixe a par ailleurs été retiré :
  la police par défaut de Minecraft ne couvre guère plus que Latin-1.
- **Lignes de liste non cliquables.** `GuiSlot.handleMouseInput()` ne traite que la molette
  et le défilement : il ne propage **pas** les clics. Un `GuiScreen` qui héberge une
  `GuiListExtended` doit relayer explicitement `mouseClicked` **et** `mouseReleased` vers la
  liste, en plus de `handleMouseInput`. Sans cela la liste s'affiche et défile parfaitement,
  mais reste inerte au clic — panne silencieuse, sans aucune erreur.
- **Pseudo de développement fixé** à `PlaytimeDev` via `args '--username'` sur la tâche
  `runClient`, pour que l'UUID hors-ligne reste stable entre deux lancements de test.
  Vérifié : le log affiche bien `Setting user: PlaytimeDev`.

### Thread-safety

`PlaytimeTracker` **et** `PlaytimeClientHandler` sont **entièrement synchronisés**. Presque tous les appels viennent du
thread client de Minecraft, mais le hook de fermeture qui vide la session à la sortie
s'exécute sur son propre thread, en parallèle d'une boucle de jeu qui peut encore ticker.
Sans ces verrous, cette sauvegarde finale pourrait s'entrelacer avec une imputation de
temps, ou fermer une session deux fois — ou pas du tout.

`PlaytimeConfig` est **immuable** et **échangée d'un bloc** (champ `volatile` dans
`PlaytimeTracker`). Un objet qu'on ne modifie jamais ne peut pas être lu à moitié écrit :
la course disparaît par construction plutôt que par discipline. Le moteur lit la
configuration via un `Supplier` à chaque usage, donc un réglage modifié en jeu s'applique
immédiatement.

---

## 10. Documentation et intégration IDE

- **`README.md`** est la documentation destinée aux joueurs et aux contributeurs :
  ce que fait le mod, installation, table de configuration, emplacement des données, FAQ,
  et la procédure de mise en place pour IntelliJ, Eclipse et VS Code. Le tenir à jour quand
  une option de configuration change.
- **`gradlew eclipse`** génère les fichiers de projet **et** les configurations de
  lancement `forge-1.12_Client.launch` / `forge-1.12_Server.launch`.
- **`gradlew genIntellijRuns`** doit être lancé **après** l'import dans IntelliJ : la tâche
  écrit dans `.idea/workspace.xml`, qui n'existe pas avant. Elle échoue sinon avec
  « Intellij workspace file could not be found ».
- **VS Code** ne peut pas lancer Minecraft directement : ForgeGradle le démarre dans une JVM
  forkée avec un classpath qu'il construit lui-même. On passe par
  `runClient --debug-jvm` (port 5005) et un attachement à distance. `.vscode/` contient les
  tâches et la configuration d'attachement prêtes à l'emploi.
- Les fichiers de projet IDE sont **générés**, donc exclus par `.gitignore`. Seuls les
  trois fichiers `.vscode/` écrits à la main sont versionnés.


---

## 11. Garde-fous automatiques

- **`gradlew :core:check` échoue si `core` importe Minecraft ou Forge.** La tâche
  `checkNoMinecraftImports` signale le fichier et la ligne. L'invariant central de
  l'architecture est ainsi tenu par l'outillage et non par la vigilance — vérifié en
  injectant volontairement un import interdit.
- **CI GitHub Actions** (`.github/workflows/build.yml`) : JDK 8, cache de la décompilation
  Minecraft, tests du moteur **avant** le setup Forge (échouer en quelques secondes plutôt
  qu'après vingt minutes de décompilation), puis build et publication du jar en artefact.
- **Encodage UTF-8 forcé** sur toutes les tâches `JavaCompile`.
- **`acceptedMinecraftVersions = "[1.12.2]"`** — strictement la version compilée et testée.

## 12. Langue

- **Code, commentaires, javadoc, fichiers de build, `.vscode`, `.gitignore`,
  `mcmod.info`** : anglais, sans exception.
- **`CLAUDE.md` et `README.md`** : français, ce sont les documents de travail.
- **Fichiers `.lang`** : c'est leur raison d'être. Parité FR/EN à maintenir — 18 clés
  aujourd'hui, aucune orpheline, aucune manquante.
- **Licence : MIT** (`LICENSE`). Une licence ne se révoque pas rétroactivement : les
  versions publiées sous MIT le restent.
