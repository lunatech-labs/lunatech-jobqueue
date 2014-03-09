#!/bin/bash

export COMMAND='/Users/nicolas/Projects/lunatech-jobqueue/bin/long-job.sh'
export QUEUE='incoming-hanmov'
while test $# -gt 0; do
        case "$1" in
                -c)
                        shift
                        if test $# -gt 0; then
                                export COMMAND=$1
                        else
                                echo "no command specified"
                                exit 1
                        fi
                        shift
                        ;;
                --command*)
                        export COMMAND=`echo $1 | sed -e 's/^[^=]*=//g'`
                        shift
                        ;;
                -id)
                        shift
                        if test $# -gt 0; then
                                export ID=$1
                        else
                                echo "no id specified"
                                exit 1
                        fi
                        shift
                        ;;
                --id*)
                        export ID=`echo $1 | sed -e 's/^[^=]*=//g'`
                        shift
                        ;;
                -h|--help)
					    echo " "
                        echo "add-job [options] queue"
                        echo " "
                        echo "options:"
                        echo "-h, --help                show brief help"
                        echo "-c, --command=COMMAND     specify a command to use"
                        echo "-id, --id=ID              specify an id for this job"
                        exit 0
                        ;;
                *)
					export QUEUE=$1
					break;
					;;
        esac
done
export JSON="{\"command\":\"$COMMAND\",\"id\":\"$ID\"}"
curl -XPOST -H 'Content-Type: application/json' -d $JSON http://localhost:9000/queues/$QUEUE/jobs

