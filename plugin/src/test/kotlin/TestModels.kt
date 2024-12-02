import Comment.Companion.referrersOn
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.javatime.CurrentDate
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.datetime
import org.statix.BelongsTo
import org.statix.HasMany
import org.statix.HasOne
import org.statix.Model


object Users : IntIdTable() {
    val name = varchar("name", 50)
}

object Posts : IntIdTable() {
    val user = reference("user_id", Users)
    val content = text("content")
}

object Comments : IntIdTable() {
    val post = reference("post_id", Posts)
    val user = reference("user_id", Users)
    val content = text("content")

    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val createdAtDate = date("created_at_date").defaultExpression(CurrentDate)
}

object Likes : IntIdTable() {
    val post = reference("post_id", Posts)
    val user = reference("user_id", Users)
}

object NullableLikes: IntIdTable() {
    val like = reference("like_id", Likes).nullable()
}

object NullableAttributes: IntIdTable() {
    val nullableString = text("nullable_string").nullable()
    val nullableInt = integer("nullable_int").nullable()
}

@Model
class NullableAttribute(id: EntityID<Int>): Entity<Int>(id) {
    companion object: EntityClass<Int, NullableAttribute>(NullableAttributes)

    var nullableString by NullableAttributes.nullableString
    var nullableInt by NullableAttributes.nullableInt
}

@Model
class NullableLike(id: EntityID<Int>): Entity<Int>(id) {
    companion object: EntityClass<Int, NullableLike>(NullableLikes)

    @HasOne
    var like by Like optionalReferencedOn NullableLikes.like
}

@Model
class User(id: EntityID<Int>) : Entity<Int>(id) {
    companion object : EntityClass<Int, User>(Users)
    var name by Users.name

    @HasMany
    val posts by Post referrersOn Posts.user

    @HasMany
    val comments by Comment referrersOn Comments.user

    @HasMany
    val likes by Like referrersOn Likes.user
}

@Model
class Post(id: EntityID<Int>) : Entity<Int>(id) {
    companion object : EntityClass<Int, Post>(Posts)

    @BelongsTo
    var user by User referencedOn Posts.user

    var content by Posts.content

    @HasMany
    val comments by Comment referrersOn Comments.post

    @HasMany
    val likes by Like referrersOn Likes.post
}

@Model
class Comment(id: EntityID<Int>) : Entity<Int>(id) {
    companion object : EntityClass<Int, Comment>(Comments)

    @BelongsTo
    var post by Post referencedOn Comments.post

    @BelongsTo
    var user by User referencedOn Comments.user
    var content by Comments.content

    val createdAt by Comments.createdAt
}

@Model
class Like(id: EntityID<Int>) : Entity<Int>(id) {
    companion object : EntityClass<Int, Like>(Likes)
    @BelongsTo
    var post by Post referencedOn Likes.post
    @BelongsTo
    var user by User referencedOn Likes.user
}

