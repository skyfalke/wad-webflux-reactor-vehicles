package wad.webflux.reactor.vehicles

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration

@SpringBootApplication
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}

@RestController
class VehiclesController {
    @Autowired
    private lateinit var rscs: RemoteServiceCallSimulator

    @GetMapping("vehicles/{vin}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getVehicleData(@PathVariable vin: String): Mono<Vehicle> =
        rscs
            .getBasicData(vin)
            .flatMap { (model, engine) ->
                Mono.zip(
                    rscs.getPictures(vin)
                        .filter { it.first == "front" }
                        .singleOrEmpty()
                        .map { it.second }
                        .switchIfEmpty(
                            Mono.defer {
                                println("Fallback Picture")
                                rscs.getSilhouette(model)
                            }
                        )
                        .onErrorResume { Mono.just("") },

                    when (engine) {
                        Engine.BEV -> rscs.getElectricRange(vin)
                            .map { electricRange ->
                                Range(electricRange, null)
                            }
                        Engine.PHEV -> Mono
                            .zip(
                                rscs.getElectricRange(vin),
                                rscs.getFuelRange(vin)
                            )
                            .map { electricAndFuelRange ->
                                Range(electricAndFuelRange.t1, electricAndFuelRange.t2)
                            }
                        Engine.CEV -> rscs.getFuelRange(vin)
                            .map { fuelRange ->
                                Range(null, fuelRange)
                            }
                    }
                        .onErrorResume { Mono.just(Range()) }
                )
                    .map { pictureAndRange ->
                        Vehicle(vin, model, engine, pictureAndRange.t1, pictureAndRange.t2)
                    }
            }
}

@Service
class RemoteServiceCallSimulator {

    fun getBasicData(vin: String): Mono<Pair<String, Engine>> = Mono
        .just("SomeElectricCar" to Engine.BEV)
        .delayElement(Duration.ofSeconds(1))

    fun getFuelRange(vin: String): Mono<Int> = Mono
        .just(512)
        .delayElement(Duration.ofSeconds(1))

    fun getElectricRange(vin: String): Mono<Int> = Mono
        .just(512)
        .delayElement(Duration.ofSeconds(1))

    fun getPictures(vin: String): Flux<Pair<String, String>> = Flux
        .just(
            "top" to "https://example.com/pictures/$vin/top.png",
            "front" to "https://example.com/pictures/$vin/front.png",
            "left" to "https://example.com/pictures/$vin/left.png",
            "right" to "https://example.com/pictures/$vin/right.png",
        )
        .delaySequence(Duration.ofSeconds(1))

    fun getSilhouette(vehicleModel: String): Mono<String> = Mono
        .just("https://example.com/pictures/$vehicleModel/silhouette.png")
        .delayElement(Duration.ofSeconds(1))
}

data class Vehicle(
    var vin: String,
    var model: String,
    var engine: Engine,
    var picture: String,
    var range: Range
)

data class Range(
    var electric: Int? = null,
    var gasoline: Int? = null
)

enum class Engine {
    BEV, PHEV, CEV
}
