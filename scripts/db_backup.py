"""Back up and restore NexusDesk's H2 database file while the app is stopped.

    python scripts/db_backup.py backup  data/nexusdesk           # -> backups/nexusdesk-YYYYMMDD-HHMMSS.mv.db
    python scripts/db_backup.py verify  backups/nexusdesk-....mv.db
    python scripts/db_backup.py restore backups/nexusdesk-....mv.db data/nexusdesk

The database argument is the path from the JDBC URL without ".mv.db" (for example data/nexusdesk-demo).
H2 keeps everything in that one file. Copying it while the app is running could catch a half-written
file, so every command first checks that H2's lock on the file is gone. A restore never deletes the
current file: it is renamed to *.before-restore-<time>.mv.db first.

Standard library only (Python 3.10+).
"""
from datetime import datetime
from pathlib import Path
import argparse
import hashlib
import os
import shutil
import sys

H2_HEADER = b"H:2"


def db_file(base: str) -> Path:
    path = Path(base)
    return path if path.name.endswith(".mv.db") else path.with_name(path.name + ".mv.db")


def in_use(path: Path) -> bool:
    """True if a running H2 still holds its lock on the file."""
    if not path.exists():
        return False
    try:
        with open(path, "rb+") as handle:
            if os.name == "nt":
                import msvcrt
                # Java locks the whole file on Windows, so even reading the first byte fails.
                handle.read(1)
                handle.seek(0)
                msvcrt.locking(handle.fileno(), msvcrt.LK_NBLCK, 1)
                msvcrt.locking(handle.fileno(), msvcrt.LK_UNLCK, 1)
            else:
                import fcntl
                fcntl.lockf(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
                fcntl.lockf(handle, fcntl.LOCK_UN)
        return False
    except OSError:
        return True


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for block in iter(lambda: handle.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def check(path: Path) -> None:
    if not path.is_file():
        raise SystemExit(f"{path} doesn't exist.")
    with open(path, "rb") as handle:
        if not handle.read(64).lstrip(b"\x00 ").startswith(H2_HEADER):
            raise SystemExit(f"{path} doesn't look like an H2 database file.")
    sidecar = Path(str(path) + ".sha256")
    if sidecar.exists():
        expected = sidecar.read_text(encoding="ascii").split()[0]
        if expected != sha256(path):
            raise SystemExit(f"{path} doesn't match its checksum. Don't restore it.")


def refuse_if_running(path: Path) -> None:
    if in_use(path):
        raise SystemExit(f"{path} is open in a running NexusDesk. Stop the app (Ctrl+C) and try again.")


def backup(args) -> None:
    source = db_file(args.database)
    if not source.is_file():
        raise SystemExit(f"No database at {source}. Check the path (it is the JDBC URL path without .mv.db).")
    refuse_if_running(source)
    target_dir = Path(args.to)
    target_dir.mkdir(parents=True, exist_ok=True)
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    target = target_dir / f"{source.name[:-len('.mv.db')]}-{stamp}.mv.db"
    shutil.copy2(source, target)
    digest = sha256(target)
    if digest != sha256(source):
        target.unlink()
        raise SystemExit("The copy doesn't match the original. Nothing was kept; try again.")
    Path(str(target) + ".sha256").write_text(f"{digest}  {target.name}\n", encoding="ascii")
    print(f"Backed up {source} -> {target} (sha256 {digest[:12]}...)")


def verify(args) -> None:
    path = Path(args.backup)
    check(path)
    print(f"{path} looks like an intact H2 file" + (" and matches its checksum." if Path(str(path) + ".sha256").exists() else "."))


def restore(args) -> None:
    backup_path = Path(args.backup)
    check(backup_path)
    target = db_file(args.database)
    refuse_if_running(target)
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.exists():
        kept = target.with_name(f"{target.name[:-len('.mv.db')]}.before-restore-{datetime.now():%Y%m%d-%H%M%S}.mv.db")
        target.rename(kept)
        print(f"Kept the current database as {kept}")
    shutil.copy2(backup_path, target)
    print(f"Restored {backup_path} -> {target}. Start NexusDesk as usual.")


def main(argv=None) -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    commands = parser.add_subparsers(dest="command", required=True)
    b = commands.add_parser("backup", help="copy a stopped database to a timestamped file")
    b.add_argument("database"); b.add_argument("--to", default="backups"); b.set_defaults(run=backup)
    v = commands.add_parser("verify", help="check a backup file")
    v.add_argument("backup"); v.set_defaults(run=verify)
    r = commands.add_parser("restore", help="put a backup back in place")
    r.add_argument("backup"); r.add_argument("database"); r.set_defaults(run=restore)
    args = parser.parse_args(argv)
    args.run(args)


if __name__ == "__main__":
    main(sys.argv[1:])
