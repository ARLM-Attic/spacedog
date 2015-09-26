package se.oort.spacedog;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.collision.btBoxShape;
import com.badlogic.gdx.physics.bullet.collision.btBroadphaseInterface;
import com.badlogic.gdx.physics.bullet.collision.btCollisionConfiguration;
import com.badlogic.gdx.physics.bullet.collision.btCollisionDispatcher;
import com.badlogic.gdx.physics.bullet.collision.btCollisionShape;
import com.badlogic.gdx.physics.bullet.collision.btDbvtBroadphase;
import com.badlogic.gdx.physics.bullet.collision.btDefaultCollisionConfiguration;
import com.badlogic.gdx.physics.bullet.collision.btDispatcher;
import com.badlogic.gdx.physics.bullet.dynamics.btConstraintSolver;
import com.badlogic.gdx.physics.bullet.dynamics.btDiscreteDynamicsWorld;
import com.badlogic.gdx.physics.bullet.dynamics.btDynamicsWorld;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;
import com.badlogic.gdx.physics.bullet.dynamics.btSequentialImpulseConstraintSolver;
import com.badlogic.gdx.physics.bullet.linearmath.btMotionState;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ArrayMap;
import com.badlogic.gdx.utils.Disposable;

public class SpaceDog extends ApplicationAdapter {

    static class MyMotionState extends btMotionState {
        Matrix4 transform;
        @Override
        public void getWorldTransform (Matrix4 worldTrans) {
            worldTrans.set(transform);
        }
        @Override
        public void setWorldTransform (Matrix4 worldTrans) {
            transform.set(worldTrans);
        }
    }

    static class GameObject extends ModelInstance implements Disposable {
        public final btRigidBody body;
        public final MyMotionState motionState;

        public GameObject(Model model, btRigidBody.btRigidBodyConstructionInfo constructionInfo) {
            super(model);
            motionState = new MyMotionState();
            motionState.transform = transform;
            body = new btRigidBody(constructionInfo);
            body.setMotionState(motionState);
        }
        public GameObject(Model model, String node, btRigidBody.btRigidBodyConstructionInfo constructionInfo) {
            super(model, node);
            motionState = new MyMotionState();
            motionState.transform = transform;
            body = new btRigidBody(constructionInfo);
            body.setMotionState(motionState);
        }

        @Override
        public void dispose () {
            body.dispose();
            motionState.dispose();
        }

        static class Constructor implements Disposable {
            public final Model model;
            public final String node;
            public final btCollisionShape shape;
            public final btRigidBody.btRigidBodyConstructionInfo constructionInfo;
            private static Vector3 localInertia = new Vector3();

            public Constructor (Model model, btCollisionShape shape, float mass) {
                this.model = model;
                this.node = null;
                this.shape = shape;
                if (mass > 0f)
                    shape.calculateLocalInertia(mass, localInertia);
                else
                    localInertia.set(0, 0, 0);
                this.constructionInfo = new btRigidBody.btRigidBodyConstructionInfo(mass, null, shape, localInertia);
            }
            public Constructor (Model model, String node, btCollisionShape shape, float mass) {
                this.model = model;
                this.node = node;
                this.shape = shape;
                if (mass > 0f)
                    shape.calculateLocalInertia(mass, localInertia);
                else
                    localInertia.set(0, 0, 0);
                this.constructionInfo = new btRigidBody.btRigidBodyConstructionInfo(mass, null, shape, localInertia);
            }

            public GameObject construct () {
                if (this.node == null) {
                    return new GameObject(model, constructionInfo);
                } else {
                    return new GameObject(model, node, constructionInfo);
                }
            }

            @Override
            public void dispose () {
                shape.dispose();
                constructionInfo.dispose();
            }
        }
    }

    // Rendering

    static String shipFile = "assets/ships/ship_1/ship_1.g3dj";
    static String skyboxFilePrefix = "assets/skybox/space";

    Environment environment;
	PerspectiveCamera cam;
	CameraInputController camController;
	ModelBatch modelBatch;
    Array<GameObject> instances;
    ArrayMap<String, GameObject.Constructor> constructors;
    AssetManager assets;
    boolean loading;

    // Physics

    btCollisionConfiguration collisionConfig;
    btDispatcher dispatcher;
    btBroadphaseInterface broadphase;
    btDynamicsWorld dynamicsWorld;
    btConstraintSolver constraintSolver;


    private void createEnvironment() {
        Bullet.init();

        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.4f, 0.4f, 0.4f, 1f));
        environment.add(new DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -0.8f, -0.2f));
        modelBatch = new ModelBatch();
        assets = new AssetManager();
        instances = new Array<GameObject>();
        constructors = new ArrayMap<String, GameObject.Constructor>(String.class, GameObject.Constructor.class);

        cam = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cam.position.set(200f, 100f, 0f);
        cam.near = 10;
        cam.far = 4000000;
        cam.lookAt(0, 0, 0);
        cam.update();
        camController = new CameraInputController(cam);
        Gdx.input.setInputProcessor(camController);

        collisionConfig = new btDefaultCollisionConfiguration();
        dispatcher = new btCollisionDispatcher(collisionConfig);
        broadphase = new btDbvtBroadphase();
        constraintSolver = new btSequentialImpulseConstraintSolver();
        dynamicsWorld = new btDiscreteDynamicsWorld(dispatcher, broadphase, constraintSolver, collisionConfig);
        dynamicsWorld.setGravity(new Vector3(0, 0, 0));
    }

    private void createModels() {
        assets.load(shipFile, Model.class);

        loading = true;
    }

	@Override
	public void create() {
        createEnvironment();
        createModels();
    }

    private void addShip() {
        Model ship = assets.get(shipFile, Model.class);
        constructors.put("ship", new GameObject.Constructor(ship, new btBoxShape(new Vector3(0.5f, 0.5f, 0.5f)), 1f));

        GameObject obj = constructors.get("ship").construct();
        obj.transform.setFromEulerAngles(MathUtils.random(360f), MathUtils.random(360f), MathUtils.random(360f));
        obj.transform.trn(MathUtils.random(-2.5f, 2.5f), 90f, MathUtils.random(-2.5f, 2.5f));
        obj.body.proceedToTransform(obj.transform);
        obj.body.setUserValue(instances.size);
        obj.body.setCollisionFlags(obj.body.getCollisionFlags());
        instances.add(obj);
        dynamicsWorld.addRigidBody(obj.body);
    }

    private void doneLoading() {
        loading = false;

        addShip();
    }

    @Override
    public void render () {
        if (loading && assets.update())
            doneLoading();

        final float delta = Math.min(1f / 30f, Gdx.graphics.getDeltaTime());

        dynamicsWorld.stepSimulation(delta, 5, 1f / 60f);

        camController.update();

        Gdx.gl.glClearColor(0.3f, 0.3f, 0.3f, 1.f);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

		modelBatch.begin(cam);
		modelBatch.render(instances, environment);
        modelBatch.end();
	}

	@Override
	public void dispose () {
        for (GameObject obj : instances)
            obj.dispose();
        instances.clear();

        for (GameObject.Constructor ctor : constructors.values())
            ctor.dispose();
        constructors.clear();

        dispatcher.dispose();
        collisionConfig.dispose();

        modelBatch.dispose();
        dynamicsWorld.dispose();
        constraintSolver.dispose();
        broadphase.dispose();
    }

	@Override public void pause () {}
	@Override public void resume () {}
	@Override public void resize (int width, int height) {}
}
