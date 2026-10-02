-- I keep resolution text in history as well, so reopening cannot erase the previous fix.
ALTER TABLE ticket_history ADD COLUMN details VARCHAR(3000);
