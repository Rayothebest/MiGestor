package com.gestor.servlet;

import com.gestor.repository.AuthRepository;
import com.gestor.repository.AuthRepository.UserSession;
import com.gestor.util.WebUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import org.mindrot.jbcrypt.BCrypt;
import java.io.IOException;
import java.sql.SQLIntegrityConstraintViolationException;

@WebServlet(urlPatterns={"/login","/register","/logout"})
public class AuthServlet extends HttpServlet {
    private final AuthRepository repo=new AuthRepository();
    @Override protected void doGet(HttpServletRequest req,HttpServletResponse res)throws ServletException,IOException{
        switch(req.getServletPath()){
            case "/logout"->{HttpSession s=req.getSession(false);if(s!=null)s.invalidate();res.sendRedirect(req.getContextPath()+"/login");}
            case "/register"->req.getRequestDispatcher("/WEB-INF/views/register.jsp").forward(req,res);
            default->{if(req.getSession(false)!=null&&req.getSession(false).getAttribute("userId")!=null){res.sendRedirect(req.getContextPath()+"/app/dashboard");return;}req.getRequestDispatcher("/WEB-INF/views/login.jsp").forward(req,res);}
        }
    }
    @Override protected void doPost(HttpServletRequest req,HttpServletResponse res)throws ServletException,IOException{
        if("/register".equals(req.getServletPath()))register(req,res);else login(req,res);
    }
    private void login(HttpServletRequest req,HttpServletResponse res)throws ServletException,IOException{
        String email=WebUtil.clean(req.getParameter("email"));String password=req.getParameter("password");
        try{UserSession u=repo.findByEmail(email);if(u==null||!u.active()||password==null||!BCrypt.checkpw(password,u.passwordHash())){req.setAttribute("error","Correo o contraseña incorrectos, o la cuenta está inactiva.");req.getRequestDispatcher("/WEB-INF/views/login.jsp").forward(req,res);return;}startSession(req,u);res.sendRedirect(req.getContextPath()+"/app/dashboard");}
        catch(Exception e){throw new ServletException("No fue posible iniciar sesión",e);}
    }
    private void register(HttpServletRequest req,HttpServletResponse res)throws ServletException,IOException{
        String name=WebUtil.clean(req.getParameter("name")),business=WebUtil.clean(req.getParameter("business")),email=WebUtil.clean(req.getParameter("email"));String password=req.getParameter("password");
        if(name.isBlank()||business.isBlank()||email.isBlank()||password==null||password.length()<8){req.setAttribute("error","Completa todos los campos. La contraseña debe tener al menos 8 caracteres.");req.getRequestDispatcher("/WEB-INF/views/register.jsp").forward(req,res);return;}
        try{UserSession u=repo.register(name,business,email,BCrypt.hashpw(password,BCrypt.gensalt(12)));startSession(req,u);res.sendRedirect(req.getContextPath()+"/app/dashboard");}
        catch(SQLIntegrityConstraintViolationException e){req.setAttribute("error","Ese correo ya está registrado.");req.getRequestDispatcher("/WEB-INF/views/register.jsp").forward(req,res);}
        catch(Exception e){throw new ServletException("No fue posible crear la cuenta",e);}
    }
    private void startSession(HttpServletRequest req,UserSession u){HttpSession old=req.getSession(false);if(old!=null)old.invalidate();HttpSession s=req.getSession(true);s.setMaxInactiveInterval(30*60);s.setAttribute("userId",u.userId());s.setAttribute("businessId",u.businessId());s.setAttribute("userName",u.userName());s.setAttribute("businessName",u.businessName());s.setAttribute("userRole",u.role());}
}
