package statix.org

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.statix.Model

object Users: IntIdTable() {
    val name = varchar("name", 50)
}

@Model
class User(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<User>(Users)

    val post by Post referrersOn Posts.userId

    val idValue = id.value
}

object Posts: IntIdTable() {
    val content = varchar("content", 255)
    val userId = reference("user_id", Users)
}

@Model
class Post(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Post>(Posts)

    val content by Posts.content
    val userId by Posts.userId

    val user by User referencedOn Posts.userId
}

