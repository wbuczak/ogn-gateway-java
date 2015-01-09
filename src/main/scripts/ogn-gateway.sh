#!/bin/bash

# OGN Project. All rights reserved


# Start/stop/restart ogn-gateway process

TIME=`date +"%F %T.%3N"`

# set the home path accordingly
OGN_GATEWAY_HOME=${HOME}/ogn/ogn-gateway

PROCESS_NAME=ogn-gateway

OGN_GATEWAY_CON_DIR=${OGN_GATEWAY_HOME}/conf
OGN_GATEWAY_LOG_DIR=${OGN_GATEWAY_HOME}/log
OGN_GATEWAY_LIB_DIR=${OGN_GATEWAY_HOME}/lib
OGN_GATEWAY_TMP_DIR=${OGN_GATEWAY_HOME}/tmp

OGN_GATEWAY_PROPERTIES=${OGN_GATEWAY_CON_DIR}/ogn-gateway.properties

OGN_GATEWAY_PROCSTATE_LOG_FILE=${OGN_GATEWAY_LOG_DIR}/${PROCESS_NAME}-procstate.log
OGN_GATEWAY_LOG4J_CONF_FILE=file://${OGN_GATEWAY_CON_DIR}/log4j.xml

OGN_GATEWAY_LOG4J_CONF_FILE=${OGN_GATEWAY_LOG_DIR}/${PROCESS_NAME}.out.log

OGN_GATEWAY_MAIN_CLASS=org.ogn.gateway.OgnGateway

OGN_GATEWAY_SRV_HOST=`hostname`
OGN_GATEWAY_DOMAIN=`hostname -d`

#remote debugging options for Eclipse
#DEBUG_OPTS="-Xdebug -Xrunjdwp:transport=dt_socket,address=5000,server=y,suspend=n"

# options used to enable remote JMX management
#JCONSOLE_REMOTE="-Dcom.sun.management.jmxremote.port=${MOBITRIP_SRV_JMX_PORT} -Dcom.sun.management.jmxremote.authenticate=true -Dcom.sun.management.jmxremote.ssl=false -Dcom.sun.management.jmxremote.password.file=conf/jmxremote.passwd -Dcom.sun.management.jmxremote.access.file=conf/jmxremote.access"

if [ ! -d ${OGN_GATEWAY_TMP_DIR} ] ; then
  mkdir ${OGN_GATEWAY_TMP_DIR}
fi

PROCESS_COMMAND=$1

PID_FILE=${OGN_GATEWAY_TMP_DIR}/ogn-gateway.pid

# Make sure the JAVA_BIN variable points to the java bin directory on your machine
JAVA_BIN=/usr/bin/java

JVM_OPTS="-Xms256m -Xmx256m -XX:+PrintGCDetails -XX:+UseParallelGC -XX:MaxGCPauseMillis=100"

RETVAL=0

# runs()
# The function will check whether a process with the
# specified PID is currently running. It will return
# 0 if the process is running, 1 if it isn't.
#
# Example: runs 23049
#
runs() {
  pid=${1##*/}
  tmp=`ps -p $pid -o pid=`
  if [ -z "$tmp" ] ; then
    return 1
  else
    return 0
  fi
}

process_start() {
  cd ${OGN_GATEWAY_HOME}

  # Check if the process is already running
  # If it is, don't start it again.
  if [ -f $PID_FILE ] ; then
    pid=`cat $PID_FILE | awk {'print $1'}`
    host=`cat $PID_FILE | awk {'print $2'}`
    runs $pid
    if [ $? -eq 1 ] ; then
      rm $PID_FILE
      really_start
    else
      echo_warning
      echo "${PROCESS_NAME} process seems to be running on host $host. Stop it first."
      echo -e "$TIME\t$OGN_GATEWAY_SRV_HOST\tSTART\tFAILED\t(running)" >> $OGN_GATEWAY_PROCSTATE_LOG_FILE
    fi
  else
    really_start
  fi
}

really_start() {

  ${JAVA_BIN} ${DEBUG_OPTS} ${JCONSOLE_REMOTE} ${JVM_OPTS} \
         -Djava.library.path=${OGN_GATEWAY_LIB_DIR} \
         -Djava.io.tmpdir=${OGN_GATEWAY_TMP_DIR} \
         -Dlog4j.configuration=${OGN_GATEWAY_LOG4J_CONF_FILE} \
         -Dprocess.log.path=${OGN_GATEWAY_LOG_DIR} \
         -Dogn.gateway.properties=${OGN_GATEWAY_PROPERTIES} \
         -classpath "${OGN_GATEWAY_LIB_DIR}/*" \
         ${OGN_GATEWAY_MAIN_CLASS} >${OGN_GATEWAY_LOG4J_CONF_FILE} 2>&1 &

  echo -n "Starting ${PROCESS_NAME} process on host ${OGN_GATEWAY_SRV_HOST} ..."

  pid="$!"
  sleep 5
  runs $pid

  if [ $? -eq 1 ] ; then
      echo_failure
      echo "${PROCESS_NAME} could not be started."
      echo
      echo -e "$TIME\t$OGN_GATEWAY_SRV_HOST\tSTART\tFAILED" >> $OGN_GATEWAY_PROCSTATE_LOG_FILE
  else
    echo "$pid $OGN_GATEWAY_SRV_HOST" > ${PID_FILE}
      echo_success
      echo
      echo -e "$TIME\t$OGN_GATEWAY_SRV_HOST\tSTART\tOK" >> $OGN_GATEWAY_PROCSTATE_LOG_FILE
  fi
}


process_stop() {
 cd ${OGN_GATEWAY_HOME}

 if [ -f $PID_FILE ] ; then

   echo -n "Stopping ${PROCESS_NAME} process ..."

   pid=`cat $PID_FILE | awk {'print $1'}`
   kill $pid >/dev/null 2>&1
   runs $pid
   proc_runs=$?
   proc_wait=0
   while [ $proc_runs -eq 0 ] ; do

     echo -n .

     sleep 1
     if [ $proc_wait -lt 10 ] ; then
       let proc_wait=$proc_wait+1
       runs $pid
       proc_runs=$?
     else
       proc_runs=1
     fi
   done
   runs $pid

   if [ $? -eq 0 ] ; then
     echo_warning
     echo
     echo -n "Unable to stop ${PROCESS_NAME} process gently... killing it..."

     kill -9 $pid
     sleep 1
     runs $pid

     if [ $? -eq 1 ] ; then
       rm -f $PID_FILE

       echo  [ OK ]
       echo -e "$TIME\$OGN_GATEWAY_SRV_HOST\tSTOP\tOK\t(kill -9)" >> $OGN_GATEWAY_PROCSTATE_LOG_FILE

       RETVAL=0
     else
       echo_failure
       echo
       echo -e "$TIME\t$OGN_GATEWAY_SRV_HOST\tSTOP\tFAILED" >> $OGN_GATEWAY_PROCSTATE_LOG_FILE
       echo "Unable to stop ${PROCESS_NAME} process."

       RETVAL=1
     fi

   else
     echo_success
     echo
     echo -e "$TIME\t$OGN_GATEWAY_SRV_HOST\tSTOP\tOK" >> $OGN_GATEWAY_PROCSTATE_LOG_FILE

     rm -f $PID_FILE
   fi

 else
    echo "Process ${PROCESS_NAME} does not seem to be running"
 fi

}


# Check whether the process is running
# The function will return 0 if the tracker is
# found to be running, 1 if it isn't.
# ----------------------------------------
process_status() {
  if [ -f $PID_FILE ]; then
    pid=`cat $PID_FILE`
    host=`cat $PID_FILE | awk {'print $2'}`
    runs $pid

    if [ $? -eq 0 ] ; then
          echo "RUNNING (host: $host  pid: $pid)"
      RETVAL=0
    else
      echo "DEAD (last time was running on host: $host) - cleaning PID file"
      rm -f $PID_FILE
      RETVAL=1
    fi
  else
      echo "STOPPED"
      RETVAL=1
  fi
  exit $RETVAL
}

# Restart: stop the process, then start it again
process_restart() {
  process_stop
  sleep 1
  process_start
}


echo_success() {
  echo "[ OK ]"
}

echo_warning() {
  echo "[ WARN ]"
}

echo_failure() {
  echo "[ FAILED ]"
}


process_printBasicUsageInfo() {
    echo "*****************************************************************************"
    echo " usage:                                                                      "
    echo " $0 start|stop|restart|status                                                "
    echo "*****************************************************************************"
}

# ##########################################################################################################
# ################################           Main Routine:             #####################################
# ##########################################################################################################

case "$PROCESS_COMMAND" in
     'start')
         process_start

     ;;

     'stop')

         process_stop

     ;;

     'restart')

         process_stop
         process_start

     ;;

     'status')
         process_status
     ;;

     *)
       process_printBasicUsageInfo
esac