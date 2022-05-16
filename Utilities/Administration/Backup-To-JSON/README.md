Forms / Subjects Backup
=======================

To backup Forms and Subjects as a set of JSON files:

1. Start the `backup_recorder.js` server.
```bash
nodejs backup_recorder.js /path/to/backup/directory
```

2. Ensure that CARDS has been started with the `BACKUP_WEBHOOK_URL`
environment variable set to `http://localhost:8000`.

3. After some Forms and Subjects have been created in CARDS, backup all
Forms and Subjects to JSON by (after logging into CARDS as `admin`)
pointing your browser to
`http://localhost:8080/Subjects.webhookbackup?dateLowerBound=1970-01-01T00:00:00`.

4. You can check the size of the backup by running `du -sh /path/to/backup/directory`.

Forms / Subjects Restore
========================

To restore Forms and Subjects into a fresh CARDS instance:

1. Ensure that CARDS has been started with the `COMPUTED_ANSWERS_DISABLED`
environment variable set to `true`. If this environment variable is not
properly set, computed answer fields will be duplicated.

2. Restore the backup by running `python3 restore_json_backup.py /path/to/backup/directory /path/to/backup/directory/FormListBackup_TIMESTAMP.json /path/to/backup/directory/SubjectListBackup_TIMESTAMP.json`.

3. Restart CARDS without the `COMPUTED_ANSWERS_DISABLED` environment variable.
