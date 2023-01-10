package server

import models.Alumno
import models.Usuario
import monitor.AulaDb
import monitor.UsersDb
import mu.KotlinLogging
import security.Cifrador
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

private val log = KotlinLogging.logger {}

private const val PUERTO = 6969

fun main() {

    // Datos del servidor
    val servidor: ServerSocket
    var cliente: Socket

    // Pool de hilos
    val pool = Executors.newFixedThreadPool(10)

    // Preparamos la DB para Usuarios
    val userDb = UsersDb()
    // Usuarios
    val users = listOf(
        Usuario("Mario", Usuario.TipoUser.ADMIN, Cifrador.codifyPassword("Hola1")),
        Usuario("Alysys", Usuario.TipoUser.USER, Cifrador.codifyPassword("Hola2"))
    )
    // Introducimos a los usuarios
    repeat(users.size) {
        userDb.add(users[it])
    }

    // Preparamos la DB para Alumnos
    val aulaDb = AulaDb()
    // Alumnos
    val alumnos = listOf(
        Alumno("L", 10),
        Alumno("Doraemon", 4),
        Alumno("Kira", 8)
    )
    // Introducimos a los alumnos
    repeat(alumnos.size) {
        aulaDb.add(alumnos[it])
    }

    // Arrancamos servidor
    log.debug { "Arrancando servidor..." }
    try {
        servidor = ServerSocket(PUERTO)
        log.debug { "\t--Servidor esperando..." }
        while (true) {
            cliente = servidor.accept()
            log.debug { "Peticion de cliente -> " + cliente.inetAddress + " --- " + cliente.port }

            val gc = GestorClientes(cliente, userDb, aulaDb)
            pool.execute(gc)
        }

    } catch (e: IllegalStateException) {
        e.printStackTrace()
    }

}