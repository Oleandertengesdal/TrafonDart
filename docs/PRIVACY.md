# Privacy: your own data

TrafonDart stores real names, so every member can see what is stored about them and remove themselves. Both live under
**Innstillinger → Dine data** (Settings → Your data).

## What is stored

| What | Why |
|---|---|
| Username, real name, preferred language | To show who played and greet you in your language |
| Password hash (BCrypt), session version | To log you in; the password itself is never stored |
| Matches and party games with every dart | The scores, statistics, ratings and awards are all computed from the darts |
| Rating history | The rating chart |
| Comments, kudos, likes | The social side of matches |
| Awards and challenges | The trophy cabinet and invitations |

Not stored: e-mail addresses, IP addresses (only kept in memory for rate limiting), or anything from your browser.
Error reports (see [OPERATIONS.md](OPERATIONS.md)) carry no name, username, IP or cookie.

## Download your data

**Last ned dataene mine** downloads one JSON file with everything about you: profile, every match with the darts you
threw, party games, your comments, kudos and likes you gave, rating history, awards, achievements and challenges.
Nothing about other people beyond their usernames as your opponents. At most 5 downloads an hour.

## Delete your account

**Slett kontoen** asks for your password and for the word SLETT (DELETE in English). Then, immediately and for good:

- Your **name is removed**: the account is renamed `deleted~<id>` and shown everywhere as "Slettet spiller" / "Deleted
  player", without a profile page. The username is free for someone new; they inherit nothing.
- You **can't log in again**: the password is replaced with an unusable one, every session ends, open reset links stop
  working, and admins can neither re-enable the account nor send it a reset link.
- Your **comments are deleted** (the text is wiped; a thread with replies keeps a "deleted" placeholder), and your
  **kudos and likes are removed**. Open **challenges** to or from you are called off.
- **Matches and results stay.** They are your opponents' history too: their records, ratings and awards would change if
  your matches disappeared. They show "Deleted player" as the opponent.

The only admin can't delete their account until someone else is made admin.

Old backups (see [DEPLOYMENT.md](DEPLOYMENT.md#6-backups)) still contain the account until they are rotated out.
