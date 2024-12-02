# Kotlin Exposed Serialized Relationships

### Serialize your Database relationships by selecting them and avoid cyclic relations 

KESR Works by generating DTO classes from your Database Entities using KSP as a compiler Plugin.
These classes then resolve the given relationship on the fly and evaluate them in the runtime.
The DTOs generated by the compiler are then accessible using the toModel() extension function on your entities.
Using the with() selector allows to specify which relationships should be resolved when serializing the models class.
> Actually the relationship will be evaluated, as soon as the responsible function is called

## Example

### User - Post - Comment example

<details>
    <summary>User.kt</summary>

    @Model
    class User(id: EntityID<Int>) : Entity<Int>(id) {
    companion object : EntityClass<Int, User>(Users)
    var name by Users.name
        @HasMany
        val posts by Post referrersOn Posts.user
    
        @HasMany
        val comments by Comment referrersOn Comments.user
    }            
</details>

<details>
    <summary>Post.kt</summary>

    @Model
    class Post(id: EntityID<Int>) : Entity<Int>(id) {
    companion object : EntityClass<Int, Post>(Posts)
        var content by Posts.content

        @BelongsTo
        var user by User referencedOn Posts.user
         
        @HasMany
        val comments by Comment referrersOn Comments.post
    }
</details>

<details>
    <summary>Comment.kt</summary>

    @Model
    class Comment(id: EntityID<Int>) : Entity<Int>(id) {
    companion object : EntityClass<Int, Comment>(Comments)
        var content by Comments.content

        @BelongsTo
        var post by Post referencedOn Comments.post
    
        @BelongsTo
        var user by User referencedOn Comments.user
    }
</details>

### Serialize Relationship
Easily serialize your relationship by using a DLS selector

```kotlin
user.toModel().with {
    posts {
        comments()
    }
    comments {
        post()
    }
}
```

### Nullable attributes
KSER can handle nullable attributes, and will render then in the final model

### Nullable relationships
KSER allows for nullable relationships, only exposing resolved relations at run time.

## Usage

#### Prerequisites

Because this library is based on KSP, Kotlin Exposed and kotlinx serialization, you will need to also add these
libraries to your project.

Currently KSER uses kotlin 2.0.21 and exposed 0.55.0

```kotlin
plugins {
    id("com.google.devtools.ksp") version "2.0.21-1.0.25"
    kotlin("plugin.serialization") version "2.0.20"
}
//for json serialization.
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

implementation("org.jetbrains.exposed:exposed-core:$kotlinExposedVersion")
implementation("org.jetbrains.exposed:exposed-dao:$kotlinExposedVersion")
```
Now the KSER libraries can be added as well

The latest release of KSER is 1.0.2

```kotlin
repositories {
    maven { setUrl("https://jitpack.io") }
}
    
implementation("com.github.StaticFX.kotlin-exposed-relationships:annotations:$kserVerion")
ksp("com.github.StaticFX.kotlin-exposed-relationships:processor:$kserVerion")
```

To test if you have installed the compiler plugin correctly, use 
``gradle kspKotlin``
 
### Code usage

#### Prerequisites

KSER builds heavily on Nullable values. Therefore attributes being null, when not set. At this time, it is not possible to use kotlinx serialization to only for example handle resolved relations. Therefore unresolved relations will be set to null. This behaviour can be controlled by configuring your formatter. 

#### JSON Example
```kotlin
private val jsonSerializer = Json {
    prettyPrint = true
    encodeDefaults = false
    explicitNulls = true
}
```

#### Usage

1. To create model classes you can use the `@Model` annotation on your desired entity.
2. Then rebuild your project using `gradle clean build`. This will generate all necessary classes and functions.
3. Afterward you should be able to import the generated classed and functions into your project.
4. Use the `toModel()` extension function to get a reference to your model.
5. Use the `with()` function to select which relationships should be resolved.
6. _optional_ - select more relationships in the `with()` context

### Attributes

KSER allows you to add custom attributes to a model using a map. These will then be serialized at runtime. 

Usage
```kotlin
model.attributes["some value"] = JsonPrimitive("Test")
```

Use kotlinx.serialization's inbuilt Json functions to build a JSON Element!
## Current limitations

1. Because this library uses KSP, there is no on the fly code generation. So you will have to run `gradle kspKotlin` everytime you annotate a new model.
2. Currently, there is no real way to get database entities from models, but this on the roadmap.

## How it works

KESR leverages KSP as a compiler addon to analyze the code you annotated with the `@Model` annotation.
All declared properties are received and filtered into generic and model properties. Generic properties include all properties not related to another model. 
Model properties are related to another model. The processor finds the by looking for the EntityID type in the property.

Then a new data class based on these properties is generated. The constructor includes all generic properties, and transient fields for the relations.
An inner Relations class is used to lazy load the relationships and provide context to the selector. 

### Example

Let's assume the given class:
```kotlin
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
```
The processor will generate the following based on this:
```kotlin
@Serializable
public data class UserModelDTO(
  public val name: String,
  public val id: Int,
  @Transient
  private val postsRelation: List<Post>? = null,
  @Transient
  private val commentsRelation: List<Comment>? = null,
  @Transient
  private val likesRelation: List<Like>? = null,
) {
  public var posts: List<PostModelDTO>? = null

  public var comments: List<CommentModelDTO>? = null

  public var likes: List<LikeModelDTO>? = null

  @Transient
  private val relations: Relations = Relations()

  public suspend fun with(block: suspend Relations.() -> Unit): UserModelDTO {
    relations.block()
    return this
  }

  public inner class Relations {
    private val posts: List<PostModelDTO> by lazy { postsRelation!!.map { it.toModel() }}

    private val comments: List<CommentModelDTO> by lazy { commentsRelation!!.map { it.toModel() }}

    private val likes: List<LikeModelDTO> by lazy { likesRelation!!.map { it.toModel() }}

    public suspend fun posts(block: suspend PostModelDTO.Relations.() -> Unit = {}) {
      dbQuery {
          this@UserModelDTO.posts = posts
      }
      posts.forEach { it.with(block) }
    }

    public suspend fun comments(block: suspend CommentModelDTO.Relations.() -> Unit = {}) {
      dbQuery {
          this@UserModelDTO.comments = comments
      }
      comments.forEach { it.with(block) }
    }

    public suspend fun likes(block: suspend LikeModelDTO.Relations.() -> Unit = {}) {
      dbQuery {
          this@UserModelDTO.likes = likes
      }
      likes.forEach { it.with(block) }
    }
  }
}
```
## Recomendations

IntelliJ's default code generation does not really handle KSP plugins well. When adding a new model, you will need to rerun the ksp task to generate the required files. This can be frustrating to work with, so i recomment to create a custom tast which executed `gradle clean build` which will make sure to generate all the files. If your build times are long, you can also try to use `gradle clean kspKotlin`.

Also adding the generated files to your IntelliJ's sources adds code completion to your project.

```kotlin
kotlin {
    sourceSets.main {
        kotlin.srcDir("build/generated/ksp/main/kotlin") <-- Check your build/generated folder structure and insert here.
    }
}
```


## Roadmap

- [ ] Add CI/CD Pipeline with automatic release
- [ ] Enhance documentation, by generating javaDocs
- [x] Find a workaround for nullable attributes
- [x] Support more custom datatypes like DateTimes
- [x] Support more attributes

## Contributing 

Every contribution is welcomed, please start by opening a new issue.


