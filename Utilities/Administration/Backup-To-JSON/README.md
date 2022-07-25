Forms / Subjects Backup
=======================

To backup Forms and Subjects as a set of JSON files:

1. Start the `backup_recorder.js` server.
```bash
nodejs backup_recorder.js /path/to/backup/directory
```

2. Ensure that CARDS has been started with the `BACKUP_WEBHOOK_URL`
environment variable set to `http://localhost:8012`.

3. After some Forms and Subjects have been created in CARDS, backup all
Forms and Subjects to JSON by (after logging into CARDS as `admin`)
pointing your browser to
`http://localhost:8080/Subjects.webhookbackup?dateLowerBound=1970-01-01T00:00:00`.

4. You can check the size of the backup by running `du -sh /path/to/backup/directory`.

Forms / Subjects Restore
========================

#### Warning

The `restore_json_backup.py` script assumes that the data to be loaded
is trusted. Loading untrusted, maliciously-formed JSON backup data could
cause arbitrary files to be uploaded to CARDS.

To restore Forms and Subjects into a fresh CARDS instance:

1. Ensure that CARDS has been started with the `COMPUTED_ANSWERS_DISABLED`
environment variable set to `true`. If this environment variable is not
properly set, computed answer fields will be duplicated.

2. Restore the backup by running `python3 restore_json_backup.py /path/to/backup/directory /path/to/backup/directory/FormListBackup_TIMESTAMP.json /path/to/backup/directory/SubjectListBackup_TIMESTAMP.json`.

    By default, `restore_json_backup.py` will use `http://localhost:8080`
as the CARDS server address and `admin` as the CARDS `admin` user password.
To set these values to something else, set the `CARDS_URL` environment
variable to the address of the CARDS server and set the `ADMIN_PASSWORD`
environment variable to the `admin` password for the CARDS server.

3. Restart CARDS without the `COMPUTED_ANSWERS_DISABLED` environment variable.

Verification Of Archived Data
=============================

To verify that a data backup is valid, that is:

- Every Subject listed in a Subjects list points to an existing and valid JSON file with a matching timestamp.
- Every Form listed in a Forms list points to an existing and valid JSON file with a matching timestamp.
  - The matching timestamp constraint can be relaxed with the `--relax_timestamp_constraint` flag and the maximum
  and minimum times before and after the backup snapshot will be printed at the end of the verification.
- Every Form's Subject is included in the backup Subjects list.
- Every file-like response in every Form is included in the backup.
- Every non-root Subject listed in a Subjects list file has its parent Subject also listed in the Subjects list file.

run:

```bash
python3 verify_complete_json_backup.py \
	--backup_directory /path/to/backup/directory \
	--form_list_file /path/to/backup/directory/form-list-file.json \
	--subject_list_file /path/to/backup/directory/subject-list-file.json
```
