# Playtime Tracker

**Combien de temps as-tu *vraiment* joué ?**

Minecraft sait compter les minutes où le jeu était ouvert. Il ne sait pas faire la
différence entre une heure passée à construire et une heure passée à faire chauffer une
ferme pendant que tu regardais une vidéo. Playtime Tracker fait cette différence.

Le mod mesure le temps passé sur chaque serveur et dans chaque monde solo, en séparant
strictement **le temps réellement joué** du **temps AFK**.

- Minecraft 1.12.2 · Forge 14.23.5.2847+
- **Client uniquement** — fonctionne sur n'importe quel serveur, sans que celui-ci ait le mod
- Aucun réseau, aucune télémétrie, tout reste sur ta machine

*[English version](README.md)*

---

## Ce que ça fait

Le compteur tourne pendant que tu joues. Dès qu'il ne détecte plus d'activité pendant
5 minutes, il s'arrête — **et il retire les 5 minutes qu'il venait de compter**. Elles
basculent dans le compteur AFK. Tu ne gagnes pas de temps de jeu en t'absentant.

Ce qui compte comme activité :

| Signal | Détail |
|---|---|
| Intention de déplacement | Avancer, reculer, strafe, sauter, s'accroupir |
| Rotation de la caméra | Bouger la vue |
| Clavier et souris | N'importe quelle touche, clic ou molette, y compris dans un inventaire |
| Interactions | Casser ou poser un bloc, ouvrir un conteneur, écrire dans le chat |

Et deux cas coupent le compteur **immédiatement**, sans attendre les 5 minutes :

- **Alt-tab** — tu as quitté la fenêtre du jeu
- **Menu pause en solo** — le monde est gelé, tu ne joues pas

### Pourquoi ça résiste aux fermes AFK

Le mod lit ton **intention** de déplacement, pas ta position.

C'est la nuance qui compte. Ta position change en permanence sans que tu fasses quoi que ce
soit : gravité, courant d'eau, minecart, monture, poussée d'un mob, correction du serveur.
Or les montages AFK classiques reposent exactement là-dessus — un canal d'eau, une boucle
de minecart. Un mod qui mesurerait le déplacement te compterait « actif » pendant toute la
nuit.

Playtime Tracker lit les commandes que tu envoies. Elles sont nulles quand tu es transporté.
**Un joueur dans un courant d'eau est déclaré AFK au bout du délai, comme il se doit.**

---

## Installation

1. Installe [Minecraft Forge](https://files.minecraftforge.net/) pour 1.12.2
2. Dépose `playtimetracker-1.12.2-x.y.z.jar` dans `.minecraft/mods/`
3. Lance le jeu

Aucune dépendance. Rien à installer côté serveur.

---

## Utilisation

Trois façons d'y accéder :

- **Échap → Statistiques → bouton « Temps de jeu »** en haut à droite
- **`/playtime`** (ou `/pt`) affiche les mêmes chiffres dans le chat
- **Une touche de ton choix** — le raccourci est enregistré non assigné, dans
  *Options → Commandes → Playtime Tracker*

L'écran affiche la destination où tu te trouves, et elle seule :

- **Session en cours** — ton état (en train de jouer / AFK depuis X), temps joué, temps AFK
- **Total ici** — cumul sur ce serveur ou ce monde, avec le pourcentage réellement joué
  (vert au-dessus de 80 %, rouge en dessous de 40 %)
- **Détails** — première connexion, nombre de sessions, durée moyenne, session la plus longue

Chaque serveur et chaque monde ont leur propre historique. Il t'attend quand tu y reviens.

**`/playtime reset`** remet à zéro la destination où tu te trouves, après confirmation. Elle
ne touche jamais aux autres ; pour tout effacer, supprime le fichier de données jeu fermé.

---

## Configuration

Deux façons de régler le mod :

- **En jeu** : écran des mods → *Playtime Tracker* → **Config**. Les changements
  s'appliquent immédiatement, sans redémarrage.
- **Par fichier** : `.minecraft/config/playtimetracker/playtimetracker.cfg`

| Option | Défaut | Ce que ça fait |
|---|---|---|
| `afkThresholdSeconds` | `300` | Inactivité à partir de laquelle le compteur s'arrête. Le temps d'inactivité écoulé est retiré du temps joué et basculé en AFK. |
| `minSessionSeconds` | `30` | Les sessions plus courtes sont ignorées entièrement, pour que les passages éclair n'encombrent pas les statistiques. `0` pour tout garder. |
| `autosaveIntervalSeconds` | `60` | Fréquence d'écriture du fichier de données. Détermine aussi ce qu'un plantage peut te coûter au maximum. |
| `retentionDays` | `90` | Durée de conservation du détail de chaque session. Au-delà, les sessions sont fusionnées en résumés mensuels — **aucun temps n'est perdu**, seul le détail disparaît. |
| `debugLogging` | `false` | Écrit chaque bascule joué ↔ AFK dans le log. Utile pour vérifier la détection ; le mod est silencieux par défaut. |

---

## Tes données

Tout est dans `.minecraft/config/playtimetracker/playtime.json`, en JSON lisible et
éditable à la main.

```
playtime.json
└── compte (UUID)
    └── serveur ou monde
        ├── sessions détaillées (90 derniers jours)
        └── résumés mensuels (au-delà)
```

Quelques garanties, parce que perdre des mois de statistiques serait absurde :

- **Écriture atomique.** Le fichier est écrit à côté puis substitué d'un bloc. Il n'est
  jamais à moitié écrit, même si le jeu meurt pendant la sauvegarde.
- **Résistance au plantage.** La session en cours est pré-enregistrée à chaque sauvegarde
  automatique. Si le jeu plante après trois heures, tu récupères tout sauf la dernière
  minute.
- **Fichier abîmé mis de côté, jamais effacé.** Il est renommé en `.corrupt-<horodatage>`
  et le mod repart proprement, pour que tu puisses tenter une récupération.
- **Séparation par compte.** L'identité est l'UUID Mojang, pas le pseudo : changer de nom
  ne coupe pas ton historique en deux.

**Realms n'est pas suivi.** Le client ne reçoit aucun identifiant stable pour une
connexion Realms — il n'y a rien sur quoi rattacher les données.

---

## Questions fréquentes

**Est-ce que ça marche sur les serveurs ?**
Oui, sur tous. Le mod est purement client : il ne parle jamais au serveur et n'a pas besoin
d'y être installé. Aucun risque d'être refusé à la connexion.

**Est-ce que fouiller mon inventaire compte comme du jeu ?**
Oui. Naviguer dans un inventaire, un coffre ou une interface de mod, c'est jouer.

**Et si je meurs et que je laisse l'écran de mort ouvert ?**
Aucune règle particulière : le délai d'inactivité normal s'applique. Si tu réapparais dans
la seconde, c'est du temps joué ; si tu pars manger, ça bascule en AFK.

**Un réseau comme Hypixel, c'est une entrée ou plusieurs ?**
Une seule, identifiée par `hôte:port`. Minecraft 1.12.2 ne donne au client aucun moyen
fiable de distinguer les sous-serveurs derrière un BungeeCord.

**Le mod m'affiche 30 % de temps réellement joué, c'est normal ?**
C'est précisément l'information que le mod existe pour donner. À toi de voir ce que tu en
fais.

---

## Développer sur le projet

### Prérequis

**JDK 8 obligatoire.** ForgeGradle 2.3 et Gradle 4.10.3 ne fonctionnent sous aucun JDK plus
récent. Le build échoue volontairement, avec un message clair, si `JAVA_HOME` pointe
ailleurs.

```bash
# Vérifier
java -version   # doit afficher 1.8

# Sinon, pour la session courante (PowerShell)
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-8.0.472.8-hotspot"
```

### Première configuration

```bash
./gradlew setupDecompWorkspace   # une seule fois — télécharge et décompile Minecraft (10 à 20 min)
./gradlew build                  # compile et produit le jar
./gradlew :core:test             # tests unitaires du moteur
./gradlew :forge-1.12:runClient  # lance Minecraft avec le mod
```

Le jar se trouve dans `forge-1.12/build/libs/`.

### IntelliJ IDEA

1. **File → Open** et sélectionne le dossier du projet. IntelliJ détecte Gradle seul.
2. Dans la fenêtre d'import, choisis le **JDK 8** comme Gradle JVM.
3. Une fois l'import terminé, exécute :
   ```bash
   ./gradlew genIntellijRuns
   ```
   > Cette tâche **doit** être lancée après l'import : elle écrit dans `.idea/workspace.xml`,
   > qui n'existe pas avant. Si elle affiche
   > *« Intellij workspace file could not be found »*, c'est que le projet n'a pas encore
   > été importé.
4. Redémarre IntelliJ. Les configurations **Minecraft Client** et **Minecraft Server**
   apparaissent dans le menu déroulant, prêtes à être lancées ou déboguées.

### Eclipse

```bash
./gradlew eclipse
```

Puis **File → Import → Existing Projects into Workspace** et sélectionne le dossier.

Les configurations de lancement sont générées automatiquement dans `forge-1.12/` :

- `forge-1.12_Client.launch`
- `forge-1.12_Server.launch`

Clic droit dessus → **Run As** ou **Debug As**. Les points d'arrêt fonctionnent
directement.

### Visual Studio Code

Installe le *Extension Pack for Java*, puis ouvre le dossier. `.vscode/settings.json`
pointe déjà VS Code vers le JDK 8.

Les tâches sont prêtes (**Ctrl+Shift+P → Run Task**) :

| Tâche | Effet |
|---|---|
| Build | `gradlew build` |
| Tests (core) | `gradlew :core:test` |
| Lancer Minecraft | Démarre le jeu |
| Lancer Minecraft (attente du débogueur) | Démarre le jeu en attendant un débogueur sur le port 5005 |

**Pour déboguer** : lance la configuration **« Attacher à Minecraft »** (F5). Elle démarre
le jeu en mode attente puis s'y connecte.

> ForgeGradle exécute Minecraft dans une JVM séparée, avec un classpath et des arguments
> qu'il construit lui-même. Aucun IDE ne peut la lancer directement — d'où le passage par
> l'attachement à distance. La même approche fonctionne dans les trois IDE :
> `./gradlew :forge-1.12:runClient --debug-jvm` ouvre le port 5005.

### Contrôle qualité

```bash
./gradlew :core:check   # tests + verification architecturale
```

`checkNoMinecraftImports` fait **échouer le build** si une classe de `core` importe
`net.minecraft.*` ou `net.minecraftforge.*`, en pointant le fichier et la ligne. La règle
d'architecture est ainsi garantie par l'outillage, pas par la vigilance.

La CI GitHub Actions exécute les tests du moteur **avant** de préparer l'espace de travail
Forge : si la logique est cassée, on le sait en quelques secondes plutôt qu'après vingt
minutes de décompilation.

### Structure du projet

```
core/          Logique métier — Java pur, AUCUNE dépendance Minecraft, couvert par 87 tests
forge-1.12/    Couche d'adaptation Forge 1.12.2 — traduit les événements du jeu, rien de plus
```

Cette séparation n'est pas décorative : **`core` n'importe jamais une classe
`net.minecraft.*`**. C'est ce qui rendra le portage vers les versions récentes de Minecraft
peu coûteux — seule la couche `forge-*` sera à réécrire.

Deux règles en découlent :

- Le temps est **injecté** dans le moteur via une interface `Clock`, jamais lu directement.
  Un test peut ainsi simuler cinq minutes d'inactivité instantanément.
- Le moteur compte en **millisecondes**, pas en ticks — les ticks se dilatent avec le lag
  du serveur et fausseraient la mesure.

```bash
./gradlew :core:test    # 87 tests, sans lancer Minecraft
./gradlew :core:check   # les tests, plus la verification qu'aucun import Minecraft n'a glisse dans core
```

---

## Licence

[MIT](LICENSE). Tu peux l'utiliser, le modifier, l'inclure dans un modpack et le
redistribuer, y compris commercialement, à condition de conserver la mention de copyright.
