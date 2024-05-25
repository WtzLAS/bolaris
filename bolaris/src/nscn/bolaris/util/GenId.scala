package nscn.bolaris.util

import cats.effect.Concurrent
import cats.effect.kernel.Clock
import cats.effect.kernel.Ref
import cats.syntax.all.toFunctorOps
import cats.syntax.all.toFlatMapOps

import scala.concurrent.duration.FiniteDuration
import cats.effect.std.AtomicCell
import cats.effect.std.Random

trait GenId[F[_]] {
  def genId: F[Long]
}

object GenId {
  private val workerIdBits = 5L
  private val rngBits = 5L
  private val maxWorkerId = -1L ^ (-1L << workerIdBits)
  private val maxRng = -1L ^ (-1L << rngBits)
  private val sequenceBits = 12L

  private val workerIdShift = sequenceBits
  private val rngShift = sequenceBits + workerIdBits
  private val timestampShift = sequenceBits + workerIdBits + rngBits
  private val sequenceMask = -1L ^ (-1L << sequenceBits)

  def apply[F[_]](using ev: GenId[F]) = ev

  def apply[F[_]: Concurrent: Clock: Random](
      workerId: Long
  ): F[GenId[F]] = {
    assert(
      workerId > 0 && workerId <= maxWorkerId,
      s"invalid workerId: required to be > 0 and < $maxWorkerId, got $workerId"
    )

    for {
      stateCell <- AtomicCell[F].of((0L, -1L))
    } yield new GenId[F] {
      override def genId: F[Long] = for {
        (sequence, timestamp) <- stateCell.evalGetAndUpdate(
          (sequence, lastTimestamp) =>
            for {
              currentTimestamp <- Clock[F].monotonic
              nextSequence =
                if (lastTimestamp == currentTimestamp.length) {
                  // believe in RNG if we overflow here
                  (sequence + 1) & sequenceMask
                } else {
                  0
                }
            } yield (nextSequence, currentTimestamp.length)
        )
        rng <- Random[F].betweenLong(0L, maxRng + 1)
      } yield (timestamp << timestampShift) | (rng << rngShift) | (workerId << workerIdShift) | sequence
    }
  }
}
