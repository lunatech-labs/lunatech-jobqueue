package com.lunatech.jobexecutor

import java.nio.file.Path

case class QueueConfig(queueName: String, queueDir: Path, spoolDir: Path, completedDir: Path, failedDir: Path, executorConfig: ExecutorConfig)