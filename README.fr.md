# Actually Played

**Combien de temps as-tu *vraiment* joué ?**

Minecraft sait compter les minutes où le jeu était ouvert. Il ne sait pas faire la
différence entre une heure passée à construire et une heure passée à faire chauffer une
ferme pendant que tu regardais une vidéo. Actually Played fait cette différence.

Le mod mesure le temps passé sur chaque serveur et dans chaque monde solo, en séparant
strictement **le temps réellement joué** du **temps AFK**.

| Minecraft | Loader |
|---|---|
| 1.7.10 | Forge 10.13.4.1614+ |
| 1.12.2 | Forge 14.23.5.2847+ |
| 1.16.5 | Forge 36.2.39+ · Fabric |
| 1.20.1 | Forge 47.2.30+ · Fabric |
| 1.21.1 | NeoForge 21.1+ · Fabric |

- **Client uniquement** — fonctionne sur n'importe quel serveur, sans que celui-ci ait le mod
- **Aucune dépendance.** Un jar dans `mods/`, rien d'autre à installer
- Aucun réseau, aucune télémétrie, tout reste sur ta machine
- Ton historique te suit : le fichier de données a le même format sur toutes les versions et
  tous les loaders

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

Actually Played lit les commandes que tu envoies. Elles sont nulles quand tu es transporté.
**Un joueur dans un courant d'eau est déclaré AFK au bout du délai, comme il se doit.**

---

## Installation

1. Installe le loader correspondant à ta version de Minecraft —
   [Forge](https://files.minecraftforge.net/), [NeoForge](https://neoforged.net/) ou
   [Fabric](https://fabricmc.net/). Sur Fabric, il te faut aussi
   [Fabric API](https://modrinth.com/mod/fabric-api), dont presque tous les mods Fabric
   dépendent.
2. Dépose le jar correspondant à ta version et à ton loader dans `.minecraft/mods/` — par
   exemple `actuallyplayed-neoforge-1.21.1-x.y.z.jar`.
3. Lance le jeu.

Rien à installer côté serveur, et rien à installer à côté du mod.

---

## Utilisation

**Échap → Statistiques → bouton « Temps de jeu »** en haut à droite, sur toutes les versions.

En **1.12.2**, deux accès supplémentaires :

- **`/played`** (ou `/ap`) affiche les mêmes chiffres dans le chat
- **Une touche de ton choix** — le raccourci est enregistré non assigné, dans
  *Options → Commandes → Actually Played*

L'écran affiche la destination où tu te trouves, et elle seule :

- **Session en cours** — ton état (en train de jouer / AFK depuis X), temps joué, temps AFK
- **Total ici** — cumul sur ce serveur ou ce monde, avec le pourcentage réellement joué
  (vert au-dessus de 80 %, rouge en dessous de 40 %)
- **Détails** — première connexion, nombre de sessions, durée moyenne, session la plus longue

Chaque serveur et chaque monde ont leur propre historique. Il t'attend quand tu y reviens.

**Signaler un bug ou une idée** en bas de l'écran ouvre le suivi des tickets, via la même
confirmation que Minecraft affiche pour un lien dans le chat — avec son bouton
« Copier dans le presse-papiers » pour les machines où aucun navigateur ne s'ouvre.

**`/played reset`** (1.12.2) remet à zéro la destination où tu te trouves, après confirmation.
Elle ne touche jamais aux autres. Sur toutes les versions, supprimer le fichier de données jeu
fermé efface tout.

---

## Configuration

**En 1.12.2**, deux façons :

- **En jeu** : écran des mods → *Actually Played* → **Config**. Les changements s'appliquent
  immédiatement, sans redémarrage.
- **Par fichier** : `.minecraft/config/actuallyplayed/actuallyplayed.cfg`

**À partir de 1.16**, par fichier uniquement :
`.minecraft/config/actuallyplayed/actuallyplayed.properties`, à éditer jeu fermé. Il est créé
au premier lancement avec les valeurs par défaut et l'explication de chaque réglage.

> Il n'y a pas d'écran de réglages sur les versions récentes, et c'est délibéré. Fabric n'en
> fournit pas, donc en proposer un imposerait d'installer deux mods de plus
> ([Cloth Config](https://modrinth.com/mod/cloth-config) et
> [Mod Menu](https://modrinth.com/mod/modmenu)) pour changer cinq valeurs. Un mod de cette
> taille doit être un jar qu'on dépose et qu'on oublie.

| Option | Défaut | Ce que ça fait |
|---|---|---|
| `afkThresholdSeconds` | `300` | Inactivité à partir de laquelle le compteur s'arrête. Le temps d'inactivité écoulé est retiré du temps joué et basculé en AFK. |
| `minSessionSeconds` | `30` | Les sessions plus courtes sont ignorées entièrement, pour que les passages éclair n'encombrent pas les statistiques. `0` pour tout garder. |
| `autosaveIntervalSeconds` | `60` | Fréquence d'écriture du fichier de données. Détermine aussi ce qu'un plantage peut te coûter au maximum. |
| `retentionDays` | `90` | Durée de conservation du détail de chaque session. Au-delà, les sessions sont fusionnées en résumés mensuels — **aucun temps n'est perdu**, seul le détail disparaît. |
| `debugLogging` | `false` | Écrit chaque bascule joué ↔ AFK dans le log. Utile pour vérifier la détection ; le mod est silencieux par défaut. |

---

## Tes données

Tout est dans `.minecraft/config/actuallyplayed/playtime.json`, en JSON lisible et
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

## Contribuer

Les rapports de bugs, les traductions et les pull requests sont les bienvenus. Tout ce qu'il
faut pour compiler le mod, configurer un IDE et lancer les tests se trouve dans
**[CONTRIBUTING.md](CONTRIBUTING.md)** (en anglais).

**Les traductions.** Le mod est livré en 27 langues : anglais, français, allemand, espagnol
(Espagne et Amérique latine), portugais (Brésil et Portugal), italien, néerlandais, suédois,
danois, finnois, polonais, tchèque, hongrois, roumain, grec, russe, ukrainien, turc,
indonésien, vietnamien, thaï, japonais, coréen et chinois (simplifié et traditionnel). Seuls
l'anglais et le français ont été écrits par un locuteur natif : **les corrections sur les
autres sont les bienvenues**, et une langue manquante aussi.

Pour en ajouter une : copie
`forge-1.12/src/main/resources/assets/actuallyplayed/lang/en_us.lang`, renomme-le selon ta
langue (`sk_sk.lang`, `bg_bg.lang`…) avec le code exact utilisé par Minecraft 1.12.2, et
traduis la partie droite de chaque ligne. Deux règles : ne touche pas aux clés, et ne mets
jamais un `%` seul dans une valeur — Minecraft passe chaque traduction dans un formateur, et
un pourcent isolé casse la ligne. Le `%s` de `actuallyplayed.gui.state.afk` est une durée et
peut se placer où ta langue le demande.

---

## Licence

[MIT](LICENSE). Tu peux l'utiliser, le modifier, l'inclure dans un modpack et le
redistribuer, y compris commercialement, à condition de conserver la mention de copyright.
