import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.statix.BelongsTo
import org.statix.HasMany
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
}

object Likes : IntIdTable() {
    val post = reference("post_id", Posts)
    val user = reference("user_id", Users)
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
}

@Model
class Like(id: EntityID<Int>) : Entity<Int>(id) {
    companion object : EntityClass<Int, Like>(Likes)
    @BelongsTo
    var post by Post referencedOn Likes.post
    @BelongsTo
    var user by User referencedOn Likes.user
}
