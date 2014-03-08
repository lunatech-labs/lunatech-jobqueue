JobQueue
========

This is a Play web application that allows jobs to be scheduled through a web interface. A job is simply a command.

Queues must be preconfigured in the the `application.conf` file. Each queue has a cofigurable number of executors, and a maximum duration for jobs.

Jobs that are queued are files in the `queue` directory. 

When they are running they are are directory the `spool` directory. If the job failed to start, ended with a non-zero exit code or didn't end within the maximum execution time, it's moved into the `failed` directory with a file `reason` that indicates the failure reason.

If a job completed succesfully it is moved into the `completed` directory.

While a job is in the spool, completed or failed directory, it also has files `job`, `stdout` and `stderr` which contain the job description, standard output and standard error, respectively.

Getting started
===============

Define queues in `application.conf`. Create the data directory and make sure it's readable for the app. Run the app and browse to `/queues`.