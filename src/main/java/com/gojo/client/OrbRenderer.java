package com.gojo.client;

import com.gojo.OrbEntity;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

/** Yungul kure: 12x8 = 96 quad, 2 qat (nuve + parilti). Shader/model yoxdur. */
public class OrbRenderer extends EntityRenderer<OrbEntity> {
    private static final Identifier[] TEX = {
            Identifier.of("gojo", "textures/entity/blue.png"),
            Identifier.of("gojo", "textures/entity/red.png"),
            Identifier.of("gojo", "textures/entity/purple.png"),
            Identifier.of("gojo", "textures/entity/void.png")};

    private static final int LON = 12, LAT = 8;
    private static final float[][][] P = new float[LAT + 1][LON + 1][3];

    static {
        for (int i = 0; i <= LAT; i++) {
            double t = Math.PI * i / LAT;
            for (int j = 0; j <= LON; j++) {
                double f = 2 * Math.PI * j / LON;
                P[i][j] = new float[]{(float) (Math.sin(t) * Math.cos(f)), (float) Math.cos(t), (float) (Math.sin(t) * Math.sin(f))};
            }
        }
    }

    public OrbRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
    }

    @Override
    public Identifier getTexture(OrbEntity e) {
        return TEX[e.getKind()];
    }

    @Override
    public boolean shouldRender(OrbEntity e, Frustum frustum, double x, double y, double z) {
        return true; // boyuk purple kesilmesin
    }

    @Override
    public void render(OrbEntity e, float yaw, float td, MatrixStack m, VertexConsumerProvider vcp, int light) {
        float s = e.scale(td);
        if (s <= 0.02f) return;
        float a = e.age + td;
        if (e.getKind() == OrbEntity.DOMAIN) {
            m.push();
            m.translate(0, 1.0, 0);
            m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(a * 0.6f));
            m.scale(s, s, s);
            sphere(vcp.getBuffer(RenderLayer.getEntityTranslucent(TEX[3])), m.peek(), 235, -1f);
            m.pop();
            return;
        }
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getEyes(TEX[e.getKind()]));

        m.push();
        m.translate(0, 1.0, 0); // bbox merkezi
        m.push();
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(a * 15f));
        m.scale(s, s, s);
        sphere(vc, m.peek(), 255);
        m.pop();

        m.push();
        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-a * 9f));
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(a * 4f));
        m.scale(s * 1.45f, s * 1.45f, s * 1.45f);
        sphere(vc, m.peek(), 70);
        m.pop();

        if (e.getKind() == OrbEntity.PURPLE) { // yadro: Purple-i Blue/Red-den fərqləndirir
            m.push();
            m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-a * 22f));
            m.scale(s * 0.55f, s * 0.55f, s * 0.55f);
            sphere(vc, m.peek(), 255);
            m.pop();
        }
        m.pop();
    }

    private static void sphere(VertexConsumer vc, MatrixStack.Entry en, int alpha) {
        sphere(vc, en, alpha, 1f);
    }

    private static void sphere(VertexConsumer vc, MatrixStack.Entry en, int alpha, float ns) {
        Matrix4f mat = en.getPositionMatrix();
        for (int i = 0; i < LAT; i++) {
            for (int j = 0; j < LON; j++) {
                float u0 = (float) j / LON, u1 = (float) (j + 1) / LON;
                float v0 = (float) i / LAT, v1 = (float) (i + 1) / LAT;
                vert(vc, en, mat, P[i][j], u0, v0, alpha, ns);
                vert(vc, en, mat, P[i + 1][j], u0, v1, alpha, ns);
                vert(vc, en, mat, P[i + 1][j + 1], u1, v1, alpha, ns);
                vert(vc, en, mat, P[i][j + 1], u1, v0, alpha, ns);
            }
        }
    }

    private static void vert(VertexConsumer vc, MatrixStack.Entry en, Matrix4f mat, float[] p, float u, float v, int a, float ns) {
        vc.vertex(mat, p[0], p[1], p[2]).color(255, 255, 255, a).texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(en, p[0] * ns, p[1] * ns, p[2] * ns);
    }
}
