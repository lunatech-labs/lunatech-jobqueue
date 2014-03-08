package com.lunatech.jobexecutor

import scala.concurrent.duration.FiniteDuration

case class ExecutorConfig(
  executors: Int = 1,
  maxExecutionTime: FiniteDuration)