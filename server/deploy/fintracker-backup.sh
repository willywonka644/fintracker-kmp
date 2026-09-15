#!/bin/sh
#
# Daily backup of the FinTracker server database (#96).
#
# Why this exists although both clients hold complete copies: the clients can
# restore the server — but nothing can restore the clients once a corrupted
# server has poisoned them during a sync. The merge decides by timestamp and
# cannot tell good data from damaged data. That is how money went missing in
# this project once before, and what saved it was a backup, not the second copy
# on the other device.
#
# Why `.backup` and not `cp`: the service keeps running. A `cp` of an open
# SQLite file can produce a copy caught mid-transaction — precisely the result
# being guarded against. `.backup` uses the backup API and yields a consistent
# state.
#
# Why the target is not next to the database: the database lives on the USB
# stick, the backup on the SD card. A failing medium then does not take both.
#
# What this does NOT cover, deliberately: total loss of the Pi — theft, water,
# lightning. An off-site copy would be another complete set of account data
# needing the same protection as the original (#85). That is a decision of its
# own, not a line in this script.
#
# Comments are English like the rest of the codebase; the messages stay German
# because a person reads them, same as the server's own error messages.

set -eu

DB="${FINTRACKER_SERVER_DB:-/srv/fintracker/fintracker.db}"
DEST="${FINTRACKER_BACKUP_DIR:-/var/backups/fintracker}"
KEEP="${FINTRACKER_BACKUP_KEEP:-7}"

# The backup is a complete set of account data and gets the same permissions as
# the original: root only.
umask 077

if [ ! -f "$DB" ]; then
    # Not a silent success. A missing database here usually means the USB stick
    # is not mounted — and that is the state in which the server would create an
    # empty database for both clients to merge against. The timer should go red
    # for this.
    echo "Keine Datenbank unter $DB. Ist /srv/fintracker eingehaengt?" >&2
    exit 1
fi

mkdir -p "$DEST"
TARGET="$DEST/fintracker-$(date +%Y-%m-%d).db"

sqlite3 "$DB" ".backup '$TARGET'"

# An unverified backup is a guess. The check costs milliseconds and is the
# difference between "a file is there" and "a usable state is there".
CHECK="$(sqlite3 "$TARGET" 'PRAGMA integrity_check;')"
if [ "$CHECK" != "ok" ]; then
    mv "$TARGET" "$TARGET.SUSPECT"
    echo "Die Sicherung ist selbst beschaedigt, aufbewahrt als $TARGET.SUSPECT: $CHECK" >&2
    exit 1
fi

# `integrity_check ok` is a statement about file structure, not about content: a
# completely empty database answers it just as happily. The worst kind of backup
# is the one that looks green and holds nothing — which is exactly what a server
# started once against an empty directory produces. So the contents go to the
# journal. Deliberately not a threshold with an invented lower bound: a fresh
# server is allowed to be empty. It still has to be visible, so that a drop from
# 562 rows to 11 shows up in the log rather than on the day someone needs the
# backup.
ZAEHLUNG="select 'Konten=' || (select count(*) from account)
              || ' Buchungen=' || (select count(*) from booking)
              || ' Kategorien=' || (select count(*) from category)
              || ' Regeln=' || (select count(*) from recurringRule);"
INHALT="$(sqlite3 "$TARGET" "$ZAEHLUNG" 2>/dev/null)" || INHALT="Inhalt nicht lesbar — fehlen die Tabellen?"

# Rotate only after the check: a failed run must never push a good older state
# out of the rotation.
ls -1t "$DEST"/fintracker-*.db 2>/dev/null | tail -n +"$((KEEP + 1))" | while IFS= read -r old; do
    rm -f "$old"
done

echo "Sicherung $TARGET angelegt, integrity_check ok, $INHALT, $(ls -1 "$DEST"/fintracker-*.db | wc -l) Staende vorhanden."
