package main.scala

import edu.umass.cs.automan.adapters.MTurk.MTurkAdapter
import edu.umass.cs.automan.core.Utilities
import edu.umass.cs.automan.adapters.MTurk.question.MTQuestionOption
import com.ebay.sdk._
import com.ebay.sdk.call._
import com.ebay.soap.eBLBaseComponents.{DetailLevelCodeType, SiteCodeType, CategoryType}
import scala.collection.mutable
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.S3ObjectSummary
import scala.collection.JavaConversions._

object eBayCategorizer extends App {
  val opts = my_optparse(args, "eBayCategorizer")
  val ebay_soap = "https://api.ebay.com/wsapi"
  val bucket_name = "dbarowyebaycategorizer"

  // init AutoMan for MTurk
  val a = MTurkAdapter { mt =>
    mt.access_key_id = opts('key)
    mt.secret_access_key = opts('secret)
    mt.sandbox_mode = opts('sandbox).toBoolean
  }

  // query eBay for product taxonomy
  val root_categories = GetAllEBayCategories()

  // a special CatNode
  val none = CatNode("none", "None of these categories apply.", Set.empty)

  // get product images from disk
  val files = new java.io.File(opts('imagedir)).listFiles

  // upload to S3
  val s3client: AmazonS3Client = init_s3()
  val image_urls = files.map { file => UploadImageToS3(s3client, file) }

  // array that says whether categorization is done
  val catdone = Array.fill[Boolean](files.size)(false)

  // array that stores taxonomies as a list
  // the head of the list is the LAST CATEGORY FOUND
  val product_taxonomies = Array.fill[List[CatNode]](files.size)(List.empty)

  // confidence values
  val product_confidences = Array.fill[List[Double]](files.size)(List.empty)

  // array that stores all of the next choices
  // initialized with the product root categories
  val taxonomy_choices = Array.fill[Set[CatNode]](files.size)(root_categories)

  while(!AllDone(catdone)) {
    // make a map from answer symbols back to CatNodes
    val answer_key = mutable.Map[Symbol,CatNode]()
    answer_key += (Symbol("none") -> CatNode("none", "None of these categories apply.", Set.empty))

    // insert each taxonomy choice into the answer key
    taxonomy_choices.foreach { catset => catset.foreach { cat => answer_key += (Symbol(cat.id) -> cat) } }

    // for each not-done product, launch a classification
    // task using the taxonomy_choices & block until done;
    // for done tasks, just propagate the leaf symbol from
    // the previous step
    val results: Array[(Symbol, Double)] = (0 until files.size).par.map { i =>
      if (!catdone(i)) {
        // Run the classifier
        val f = Classify(image_urls(i), GetOptions(taxonomy_choices(i)))
        // the "()" and ".value" extract the value from the Future and RadioButtonAnswer respectively
        (f().value, f().confidence)
      } else {
        (Symbol(product_taxonomies(i).head.id), 1.0)
      }
    }.toArray

    (0 until files.size).foreach { i => if (!catdone(i)) {
      // update product taxonomies
      product_taxonomies(i) = answer_key(results(i)._1) :: product_taxonomies(i)
      // update confidence values
      product_confidences(i) = results(i)._2 :: product_confidences(i)
      // update catdone
      catdone(i) = answer_key(results(i)._1).is_leaf
      // get the next set of choices
      taxonomy_choices(i) = answer_key(results(i)._1).Children
    } }
  }

  // print output
  (0 until files.size).foreach { i => println("For file \"" + files(i).toString() +
                                              "\": " + pretty_print_taxonomy(product_taxonomies(i)) +
                                              ", confidence = " + product_confidences(i).mkString(" * ") +
                                              " = " + product_confidences(i).reduceLeft((acc,c) => acc * c).toString())}

  println("Done.")

  private def pretty_print_taxonomy(tax: List[CatNode]) : String = {
    tax.reverse.map { cn => cn.name}.mkString(" > ")
  }

  private def init_s3() : AmazonS3Client = {
    import com.amazonaws.auth.BasicAWSCredentials

    val awsAccessKey = opts('key)
    val awsSecretKey = opts('secret)
    val c = new BasicAWSCredentials(awsAccessKey, awsSecretKey)
    val s3 = new AmazonS3Client(c)

    // delete bucket and all of its contents before recreating
    if (s3.doesBucketExist(bucket_name)) {
      val files: java.util.List[S3ObjectSummary] = s3.listObjects(bucket_name).getObjectSummaries
      for (file <- files) {
        s3.deleteObject(bucket_name, file.getKey)
      }
      s3.deleteBucket(bucket_name)
    }

    // create
    s3.createBucket(bucket_name)
    s3
  }

//  void deleteObjectsInFolder(String bucketName, String folderPath) {
//    for (S3ObjectSummary file : s3.listObjects(bucketName, folderPath).getObjectSummaries())
//    s3.deleteObject(bucketName, file.getKey())
//  }

  private def UploadImageToS3(s3client: AmazonS3Client, image_file: java.io.File) : String = {
    import java.util.Calendar
    import com.amazonaws.services.s3.model.{PutObjectRequest, CannedAccessControlList}

    // upload
    s3client.putObject(new PutObjectRequest(bucket_name, image_file.getName, image_file).withCannedAcl(CannedAccessControlList.PublicRead))

    // allow public read access
    val cal = Calendar.getInstance()
    cal.add(Calendar.WEEK_OF_YEAR, 2)
    s3client.generatePresignedUrl(bucket_name, image_file.getName, cal.getTime).toString
  }

  private def GetOptions(choices: Set[CatNode]) : List[MTQuestionOption] = {
    new MTQuestionOption(Symbol(none.id), none.name, "") :: choices.toList.map { node => new MTQuestionOption(Symbol(node.id), node.name, "") }
  }

  private def AllDone(completion_arr: Array[Boolean]) = completion_arr.foldLeft(true)((acc, tval) => acc && tval)

  def Classify(image_url: String, options: List[MTQuestionOption]) = a.RadioButtonQuestion { q =>
    q.title = "Please choose the appropriate category for this image"
    q.text = "Please choose the appropriate category for this image"
    q.image_url = image_url
    q.options = options
  }

  private def my_optparse(args: Array[String], invoked_as_name: String) : Utilities.OptionMap = {
    val usage = "Usage: " + invoked_as_name + " -k [AWS key] -s [AWS secret] -e [eBay key] -i [image directory] [--sandbox [true | false]]" +
      "\n  NOTE: passing credentials this way will expose" +
      "\n  them to users on this system."
    if (args.length != 8 && args.length != 10) {
      println("You only supplied " + args.length + " arguments.")
      println(usage)
      sys.exit(1)
    }
    val arglist = args.toList
    val opts = nextOption(Map(),arglist)
    if(!opts.contains('sandbox)) {
      opts ++ Map('sandbox -> true.toString)
    } else {
      opts
    }
  }

  private def nextOption(map : Utilities.OptionMap, list: List[String]) : Utilities.OptionMap = {
    list match {
      case Nil => map
      case "-k" :: value :: tail => nextOption(map ++ Map('key -> value), tail)
      case "-s" :: value :: tail => nextOption(map ++ Map('secret -> value), tail)
      case "-e" :: value :: tail => nextOption(map ++ Map('ebay_key -> value), tail)
      case "-i" :: value :: tail => nextOption(map ++ Map('imagedir -> value), tail)
      case "--sandbox" :: value :: tail => nextOption(map ++ Map('sandbox -> value), tail)
      case option :: tail => println("Unknown option "+option)
        sys.exit(1)
    }
  }

  private def initEBay() : ApiContext = {
    val apiContext = new ApiContext()
    apiContext.getApiCredential.seteBayToken(opts('ebay_key))
    apiContext.setApiServerUrl(ebay_soap)
    apiContext
  }

  def GetAllEBayCategories() : Set[CatNode] = {
    class CategoryHandler extends CategoryEventListener {
      private val _cats = new mutable.HashSet[CategoryType]()
      private val _catids = mutable.Map[String,CategoryType]()
      private val _parent_children_map = mutable.Map[String,Set[String]]()
      private val _forest = mutable.Map[String,CatNode]()
      private val _roots = mutable.Set[CatNode]()
      def receivedCategories(siteID: SiteCodeType, categories: Array[CategoryType], categoryVersion: String): Unit = {
        for(category <- categories) {
          val id = category.getCategoryID
          _cats.add(category)
          _catids += (id -> category)
          val parents = category.getCategoryParentID
          for(parent_id <- parents) {
            if (_parent_children_map.contains(parent_id)) {
              val siblings = _parent_children_map(parent_id)
              if (siblings.contains(parent_id)) {
                throw new Exception("Parent is sibling of child! This id = " + category.getCategoryID + ", parents: " + parents.mkString(", "))
              }
              _parent_children_map += (parent_id -> siblings.union(Set(id)))
            } else {
              // idiotically, top-level parents are parents of themselves (have self-loops)
              // so don't propigate the foolishness
              if (parent_id != id) {
                _parent_children_map += (parent_id -> Set(id))
              }
            }
          }
        }
      }

      def Roots = _roots

      def Categories = _cats

      private def BuildCatForest : Unit = {
        for(cat <- _cats) {
          Add(cat)
        }
      }

      private def Add(cat: CategoryType) : CatNode = {
        val id = cat.getCategoryID
        val parents = mutable.Set[CatNode]()
        var cn_opt : Option[CatNode] = None

        if (!_forest.contains(id)) {
          // is this category a root?
          if (cat.getCategoryLevel == 1) {
            // add to _roots
            val cn = CatNode(id, cat.getCategoryName, Set.empty)
            _roots += cn
            cn_opt = Some(cn)
          // if not, add parents
          } else {
            // populate parents set with CatNodes for parent nodes
            for (parent <- cat.getCategoryParentID) {
              parents.add(Add(_catids(parent)))
            }

            cn_opt = Some(CatNode(id, cat.getCategoryName, parents.toSet))
          }
        }

        cn_opt match {
          // if a CatNode was constructed above, add to forest
          case Some(cn) =>
            _forest += (id -> cn)
            cn
          // otherwise, just retrieve from data structure
          case None => _forest(id)
        }
      }

      private def AddChildren(roots: Set[CatNode]) : Unit = {
        if (!roots.isEmpty) {
          for(root <- roots) {
            // get children ids for root id
            val children_ids = if (_parent_children_map.contains(root.id)) {
              _parent_children_map(root.id)
            } else {
              Set.empty
            }

            val children = mutable.Set[CatNode]()
            for(child_id <- children_ids) {
              if (child_id == root.id) {
                throw new Exception("Child (" + child_id + ") is the same as parent (" + root.id + ")")
              }
              val child = _forest(child_id)
              root.addChild(child)
              children.add(child)
            }

            // recurse
            AddChildren(children.toSet)
          }
        }
      }

      // this method counts the number of nodes in the taxonomy
      private def SanityCheck(roots: Set[CatNode]) : Int = {
        val count = roots.size
        count + roots.foldLeft(0) { (sum, node) => sum + SanityCheck(node.Children) }
      }

      def GetGraph : Set[CatNode] = {
        BuildCatForest
        AddChildren(_roots.toSet)

        // make sure that the number of nodes in the graph
        // is the same as the number of categories in the
        // original XML
        val count = SanityCheck(_roots.toSet)
        if (count != _forest.size) {
          throw new Exception("The in-memory taxonomy size of " + count + " nodes is not the same as the XML data of " + _forest.size + " nodes!")
        }

        _roots.toSet
      }
    }

    // init ebay
    val ebay = initEBay()

    // get categories
    val catobj = new CategoryHandler()
    GetCategoriesCall.getAllCategories(ebay, SiteCodeType.US, 1, DetailLevelCodeType.RETURN_ALL, 100, catobj)

    // return graph
    catobj.GetGraph
  }

  case class CatNode(id: String, name: String, parent: Set[CatNode]) {
    val _children = mutable.Set[CatNode]()
    def addChild(child: CatNode) = _children.add(child)
    def Children = _children.toSet
    def is_leaf = _children.size == 0
  }
}