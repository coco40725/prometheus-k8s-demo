package com.example

import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import kotlin.math.floor
import kotlin.math.sqrt

@Path("/hello")
class ExampleResource {

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    fun hello() = "Hello from Quarkus REST"

    @GET
    @Path("prime/{number}")
    fun checkIfPrime(@PathParam("number") number: Long): String {
        if (number < 1) {
            return "Only natural numbers can be prime numbers."
        }
        if (number == 1L) {
            return "$number is not prime."
        }
        if (number == 2L || number % 2 == 0L) {
            return "$number is not prime."
        }
        return if (testPrimeNumber(number)) {
            "$number is prime."
        } else {
            "$number is not prime."
        }
    }

    protected fun testPrimeNumber(number: Long): Boolean {
        var i = 3
        while (i < floor(sqrt(number.toDouble())) + 1) {
            if (number % i == 0L) {
                return false
            }
            i += 2
        }
        return true
    }
}