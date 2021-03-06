package com.workday.torque

import com.linkedin.dex.parser.TestMethod
import com.workday.torque.pooling.TestChunk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.util.concurrent.TimeUnit

class TestChunkRetryer(private val adbDevice: AdbDevice,
                       private val args: Args,
                       private val logcatFileIO: LogcatFileIO,
                       private val testChunkRunner: TestChunkRunner,
                       private val installer: Installer,
                       private val screenRecorder: ScreenRecorder = ScreenRecorder(adbDevice, args)
) {

    suspend fun runTestChunkWithRetry(testChunk: TestChunk): List<AdbDeviceTestResult> {
        val timeoutMillis = getTimeoutMillis(args, installer, testChunk)

        return try {
            withTimeout(timeoutMillis) {
                var resultsResponse = testChunkRunner.run(args, testChunk)
                while (resultsResponse.needToRetry(testChunk, args.retriesPerChunk)) {
                    adbDevice.log("Chunk has failed tests, retry count: ${testChunk.retryCount++}/${args.retriesPerChunk}, retrying...")
                    resultsResponse = if (args.recordFailedTests && isLastRun(testChunk.retryCount, args.retriesPerChunk)) {
                        runTestChunkInIndividualRecordedChunks(testChunk)
                    } else {
                        testChunkRunner.run(args, testChunk)
                    }
                }
                resultsResponse
            }
        } catch (e: TimeoutCancellationException) {
            adbDevice.log("TestChunk retry timeout on ${adbDevice.tag}, tests ran in chunk: ${testChunk.testMethods.getTestNames()}")
            createTimedOutAdbDeviceTestResults(testChunk, timeoutMillis, e)
        }
    }

    private fun getTimeoutMillis(args: Args, installer: Installer, testChunk: TestChunk): Long {
        val allowedChunkRunCount = args.retriesPerChunk + 1
        val allowedInstallCount = args.retriesInstallPerApk + 1
        val chunkTimeOutWithRetries = TimeUnit.SECONDS.toMillis(args.chunkTimeoutSeconds) * allowedChunkRunCount
        val installTimeOutWithRetries = TimeUnit.SECONDS.toMillis(args.installTimeoutSeconds.toLong()) * allowedChunkRunCount * allowedInstallCount

        return if (installer.isChunkApkInstalled(testChunk)) {
            chunkTimeOutWithRetries
        } else {
            chunkTimeOutWithRetries + installTimeOutWithRetries
        }
    }

    private fun List<TestMethod>.getTestNames() = map { it.testName }

    private fun List<AdbDeviceTestResult>?.needToRetry(testChunk: TestChunk, retriesPerChunk: Int): Boolean {
        return (this == null || hasFailedTests()) && isRetryable(testChunk.retryCount, retriesPerChunk)
    }

    private fun List<AdbDeviceTestResult>.hasFailedTests(): Boolean {
        return any { it.status is AdbDeviceTestResult.Status.Failed }
    }

    private fun isRetryable(retryCount: Int, maxRetryCount: Int) = retryCount < maxRetryCount

    private fun isLastRun(retryCount: Int, maxRetryCount: Int) = retryCount == maxRetryCount

    private suspend fun CoroutineScope.runTestChunkInIndividualRecordedChunks(testChunk: TestChunk): List<AdbDeviceTestResult> {
        val resultsResponse = mutableListOf<AdbDeviceTestResult>()
        testChunk.toSingleTestChunks().forEach { singleTestChunk ->
            screenRecorder.start(this, singleTestChunk.getTestDetails())
            val singleTestResultList = testChunkRunner.run(args, singleTestChunk)
            resultsResponse.addAll(singleTestResultList)
            screenRecorder.stop()
            if (!singleTestResultList.hasFailedTests()) {
                screenRecorder.removeLastFile()
            }
        }
        return resultsResponse
    }

    private fun TestChunk.toSingleTestChunks(): List<TestChunk> {
        return testMethods.map {
            TestChunk(
                    index = index,
                    testModuleInfo = testModuleInfo,
                    testMethods = listOf(it),
                    retryCount = retryCount
            )
        }
    }

    private fun TestChunk.getTestDetails(): TestDetails {
        return testMethods.single().toTestDetails()
    }

    private fun createTimedOutAdbDeviceTestResults(
            testChunk: TestChunk,
            timeoutMillis: Long,
            e: TimeoutCancellationException
    ): List<AdbDeviceTestResult> {
        return testChunk.testMethods
                .map { it.toTestDetails() }
                .map { testDetails: TestDetails ->
                    AdbDeviceTestResult(
                            adbDevice = adbDevice,
                            className = testDetails.testClass,
                            testName = testDetails.testName,
                            status = AdbDeviceTestResult.Status.Failed("Timed out with exception: ${e.message}"),
                            durationMillis = timeoutMillis,
                            logcatFile = logcatFileIO.getLogcatFileForTest(testDetails))
                }
    }
}