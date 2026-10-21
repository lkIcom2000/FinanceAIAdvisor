-- Runs after every migrate (not checksummed): the application role's password comes only from the environment.
ALTER ROLE fine_app PASSWORD '${appDbPassword}';
