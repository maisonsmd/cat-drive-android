package com.meomeo.catdrive.utils

import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

class Misc {
    companion object {
        /**
         * Convert a map to a string of key-value pairs, line by line
         */
        fun toKeyValString(map: Map<String, String>): String {
            var result = ""
            var count = 1

            for ((key, value) in map) {
                result += "$key=$value"
                if (count < map.size) {
                    result += "\n"
                }
                count++
            }

            return result
        }

        fun md5(s: ByteArray): String {
            return try {
                // Create MD5 Hash
                val messageDigest = MessageDigest.getInstance("MD5").digest(s)
                // Create Hex String
                val hexString = StringBuilder()
                for (aMessageDigest in messageDigest) {
                    var h = Integer.toHexString(0xFF and aMessageDigest.toInt())
                    while (h.length < 2) {
                        h = "0$h"
                    }
                    hexString.append(h)
                }
                hexString.toString()
            } catch (e: NoSuchAlgorithmException) {
                e.printStackTrace()
                ""
            }
        }

        fun sanitize(str: String): String {
            // Replace special characters from Google Map
            return str.replace("\u00a0", " ").replace("\n", " ").replace("…", "...")
        }
    }
}