DELETE
FROM error
WHERE message = 'Job was cancelled'
   OR message = 'Cannot write to channel'