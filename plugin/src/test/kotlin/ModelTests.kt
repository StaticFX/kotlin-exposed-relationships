import generated.toModel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals

class ModelTests {

    private val jsonSerializer = Json {
        prettyPrint = true
    }

    private fun buildDatabases() {
        transaction {
            SchemaUtils.createMissingTablesAndColumns(Users, Posts, Likes, Comments, NullableLikes)
        }
    }

    private fun prepareDB() {
        connectToDB()
        buildDatabases()
    }

    private fun prepareUser(): User {
        return transaction {
            User.new {
                this.name = "TestUser"
            }
        }
    }

    private fun prepareComment(user: User, post: Post): Comment {
        return transaction {
            Comment.new {
                this.user = user
                this.post = post
                this.content = "Comment"
            }
        }
    }

    private fun preparePost(owner: User): Post {
        return transaction {
            Post.new {
                this.user = owner
                this.content = "This is a post"
            }
        }
    }

    @Test
    fun testSingleModel() {
        prepareDB()
        val user = prepareUser()

        val model = runBlocking {
            user.toModel()
        }

        val json = jsonSerializer.encodeToJsonElement(model)
        assertEquals("""{"name":"TestUser","id":${model.id}}""", json.toString())
    }

    @Test
    fun testSingleWithEmptyRelation() {
        prepareDB()
        val user = prepareUser()

        val model = runBlocking {
            user.toModel().with { posts() }
        }

        val json = jsonSerializer.encodeToJsonElement(model)
        assertEquals(json.toString(),"""{"name":"TestUser","id":${model.id},"posts":[]}""")
    }

    @Test
    fun testSingleWithFilledRelation() {
        prepareDB()
        val user = prepareUser()
        val post = preparePost(user)

        val model = runBlocking {
            user.toModel().with { posts() }
        }

        val json = jsonSerializer.encodeToJsonElement(model)
        assertEquals("""{"name":"TestUser","id":${model.id},"posts":[{"content":"This is a post","id":${post.id.value}}]}""", json.toString())
    }

    @Test
    fun testMultipleRelationShips() {
        prepareDB()
        val user = prepareUser()
        val post = preparePost(user)
        val comment = prepareComment(user, post)

        val model = runBlocking {
            user.toModel().with {
                posts {
                    comments()
                }
                comments {
                    post()
                }
            }
        }

        val createdAt = transaction { comment.createdAt }

        val json = jsonSerializer.encodeToJsonElement(model)
        assertEquals("""{"name":"TestUser","id":3,"posts":[{"content":"This is a post","id":2,"comments":[{"content":"Comment","createdAt":"$createdAt","id":1}]}],"comments":[{"content":"Comment","createdAt":"$createdAt","id":1,"post":{"content":"This is a post","id":2}}]}""", json.toString())
    }


    @Test
    fun testAttributes() {
        prepareDB()
        val user = prepareUser()
        val model = transaction { user.toModel() }

        model.attributes["some value"] = JsonPrimitive("Test")
        val json = jsonSerializer.encodeToJsonElement(model)
        assertEquals("""{"name":"TestUser","id":${user.id},"attributes":{"some value":"Test"}}""", json.toString())
    }

    @Test
    fun testNullableRelations() {
        prepareDB()
        val nullableLike = transaction { NullableLike.new { like = null  } }
        val nullableLikeModel = nullableLike.toModel()

        runBlocking {
            nullableLikeModel.with { like() }
        }

        assertEquals("""{"id":${nullableLikeModel.id}}""", jsonSerializer.encodeToJsonElement(nullableLikeModel).toString())

        val user = prepareUser()
        val post = preparePost(user)
        val like = transaction { Like.new {
            this.user = user
            this.post = post
        } }

        val likeID = transaction { like.id.value }

        transaction { nullableLike.like = like }
        println(transaction { nullableLike.like })
        val newModel = runBlocking { nullableLike.toModel().with { like() } }

        println()
        assertEquals("""{"id":${newModel.id},"like":{"id":${likeID}}}""", jsonSerializer.encodeToJsonElement(newModel).toString())
    }
}