-- Version 0.1.0 hashed passwords with Spring's deprecated Pbkdf2PasswordEncoder constructor,
-- which used PBKDF2-HMAC-SHA1 without saying so. I tag those hashes so the delegating encoder
-- knows how to check them. Each one is replaced with PBKDF2-HMAC-SHA256 at that person's next
-- successful sign-in, so nobody's password has to be reset.
UPDATE users SET password_hash = CONCAT('{pbkdf2-sha1}', password_hash) WHERE password_hash NOT LIKE '{%';
