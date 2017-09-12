package kategory.optics

import kategory.Applicative
import kategory.Either
import kategory.HK
import kategory.Option
import kategory.Tuple2
import kategory.compose
import kategory.identity
import kategory.none
import kategory.some
import kategory.toT

/**
 * A [Optional] can be seen as a pair of functions `getOption: (A) -> Option<B>` and `set: (B) -> (A) -> A`
 *
 * An [Optional] can also be defined as a weaker [Lens] and [Prism]
 *
 * @param A the source of a [Optional]
 * @param B the target of a [Optional]
 * @property getOption from an `A` we can extract a `Option<B?`
 * @property set replace the target value by `B` in an `A` so we obtain another modified `A`
 * @constructor Creates a Lens of type `A` with target `B`.
 */
abstract class Optional<A, B> {

    /** get the target of a [Optional] or nothing if there is no target */
    abstract fun getOption(a: A): Option<B>

    /** get the modified source of a [Optional] */
    abstract fun set(b: B): (A) -> (A)

    companion object {
        operator fun <A, B> invoke(getOption: (A) -> Option<B>, set: (B) -> (A) -> (A)) = object : Optional<A, B>() {
            override fun getOption(a: A): Option<B> = getOption(a)

            override fun set(b: B): (A) -> A = set(b)
        }

        fun <A, B> void() = Optional<A, B>(
                { none() },
                { _ -> { b -> identity(b) } }
        )

    }

    /** get the target of a [Optional] or return the original value while allowing the type to change if it does not match */
    fun getOrModify(a: A): Either<A, B> = getOption(a).fold({ Either.Left(a) }, { Either.Right(it) })

    /** check if there is no target */
    fun isEmpty(a: A): Boolean = !getOption(a).nonEmpty

    /** check if there is a target */
    fun nonEmpty(a: A): Boolean = getOption(a).nonEmpty

    /** find if the target satisfies the predicate */
    fun find(p: (B) -> Boolean): (A) -> Option<B> =
            { a -> getOption(a).flatMap { b -> if (p(b)) b.some() else none() } }

    /** check if there is a target and it satisfies the predicate */
    fun exists(p: (B) -> Boolean): (A) -> Boolean =
            { a -> getOption(a).fold({ false }, p) }

    /** check if there is no target or the target satisfies the predicate */
    fun all(p: (B) -> Boolean): (A) -> Boolean =
            { a -> getOption(a).fold({ true }, p) }

    /** join two [Optional] with the same target */
    fun <C> choice(other: Optional<C, B>): Optional<Either<A, C>, B> =
            Optional(
                    { a -> a.fold(this::getOption, other::getOption) },
                    { b -> { it.bimap(this.set(b), other.set(b)) } }
            )

    fun <C> first(): Optional<Tuple2<A, C>, Tuple2<B, C>> =
            Optional(
                    { (a, c) -> getOption(a).map { it toT c } },
                    { (b, c) -> { (a, c2) -> setOption(b)(a).fold({ set(b)(a) toT c2 }, { it toT c }) } }
            )

    fun <C> second(): Optional<Tuple2<C, A>, Tuple2<C, B>> =
            Optional(
                    { (c, a) -> getOption(a).map { c toT it } },
                    { (c, b) -> { (c2, a) -> setOption(b)(a).fold({ c2 toT set(b)(a) }, { c toT it }) } }
            )

    /** modify polymorphically the target of a [Optional] with a function */
    inline fun modify(crossinline f: (B) -> B): (A) -> A = { a -> getOption(a).fold({ a }, { set(f(it))(a) }) }

    /** modify polymorphically the target of a [Optional] with an Applicative function */
    inline fun <reified F> modifyF(FA: Applicative<F> = kategory.applicative(), crossinline f: (B) -> HK<F, B>, a: A): HK<F, A> =
            getOrModify(a).fold(
                    { FA.pure(it) },
                    { FA.map(f(it), { set(it)(a) }) }
            )

    /**
     * modify polymorphically the target of a [Optional] with a function.
     * return empty if the [Optional] is not matching
     */
    fun modifiyOption(f: (B) -> B): (A) -> Option<A> = { a -> getOption(a).map({ set(f(it))(a) }) }

    /**
     * set polymorphically the target of a [Optional] with a value.
     * return empty if the [Optional] is not matching
     */
    fun setOption(b: B): (A) -> Option<A> = modifiyOption { b }

    /** compose a [Optional] with a [Optional] */
    infix fun <C> composeOptional(other: Optional<B, C>): Optional<A, C> = Optional(
            { a -> getOption(a).flatMap(other::getOption) },
            this::modify compose other::set
    )

    /** compose a [Optional] with a [Prism] */
    infix fun <C> composePrism(other: Prism<B, C>): Optional<A, C> = composeOptional(other.asOptional())

    /** compose a [Optional] with a [Lens] */
    infix fun <C> composeLens(other: Lens<B, C>): Optional<A, C> = composeOptional(other.asOptional())

    /**
     * Plus operator overload to compose optionals
     */
    operator fun <C> plus(o: Optional<B, C>): Optional<A, C> = composeOptional(o)

    operator fun <C> plus(o: Prism<B, C>): Optional<A, C> = composePrism(o)

    operator fun <C> plus(o: Lens<B, C>): Optional<A, C> = composeLens(o)
}