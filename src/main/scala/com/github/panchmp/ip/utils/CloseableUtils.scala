package com.github.panchmp.ip.utils

import java.io.Closeable

object CloseableUtils {
  def using[A, B <: Closeable](closeable: B)(f: B => A): A = {
    require(closeable != null, "resource is null")
    try {
      f(closeable)
    } finally {
      closeable.close()
    }
  }
}
