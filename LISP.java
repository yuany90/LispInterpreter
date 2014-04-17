/*
 * The project was to code the Lisp metacircular interpreter in Java.
 * Final project of CS655
 * By Yuan Lu
 */
//package lisp;
import java.io.*;
public class LISP {
    static boolean ASTerror = false; // Detecting Syntax error
    // By "environment", I mean scope. Since there is only one scope for LISP.
    // I can refer to it as "environment"
    static envASTnode environment = null;
    static class ASTnode implements Cloneable{ //ASTnode means abstract systax tree
        boolean error = false; // Detecting Syntax error inside this node
        // A way to find out which class a node belongs to, for we will see its
        // many subclasses
        String id = "";
        String errorMsg = ""; // Record the exact syntax error
        int quote = 0; // Indicate how many times the node was quoted
        // The field asdata is really important, preventing nodes from over
        // "evaluated". I spent an hour to come up with it.
        boolean asdata = false;
        ASTnode next = null;  // Linked list
        ASTnode sub = null;
        public ASTnode(){};
        // Treat itself as data. As you will see in eval(),
        // it will return itself all the times.
        public ASTnode treatasdata(){
            asdata = true;
            return this;
        }

        public ASTnode eval(envASTnode env){ // To be overrided
            if(asdata){
                return this;
            }
            if(next == null){
                error = true;
                return this;
            }
            return next.eval(env);
        }

        public ASTnode clone() throws CloneNotSupportedException {
            return (ASTnode)(super.clone());
        }
        // Important. Not only keep the original ones, but also prevent the
        // ASTnode List from forming a circle. Java's clone is shallow copy.
        // We need to make a deep copy ourselves.
        public ASTnode cloneDeep(){
            try{
            ASTnode clone = this.clone();
            if(next !=null){
                ASTnode cloneNext = next.cloneDeep();
                clone.next = cloneNext;
            }
            if(sub!= null){
                ASTnode cloneSub = sub.cloneDeep();
                clone.sub = cloneSub;
            }
            return clone;
            } catch(Exception e){
                return null;
            }
        }
        // print itself
        public void print(){
            if(error){
                System.out.println("Wrong input");
                System.out.print(errorMsg);
            } else {
                for(int i= 0; i< quote; i++){
                    System.out.print("'");
                }
                System.out.print(id+' ');
                if(sub != null){
                    System.out.print('['); // Distinguish from input
                    sub.print();
                    System.out.print(']');
                }
                if(next != null){
                    next.print();
                }
            }
        };
        public String toString(){
                StringBuffer sb = new StringBuffer(); //StringBuffer
                for(int i= 0; i< quote; i++){
                    sb.append("'");
                }
                sb.append(id+" ");
                if(sub != null){
                    sb.append("[");
                    sub.toString();
                    sb.append("]");
                }
                if(next != null){
                    next.toString();
                }
                return sb.toString();
        };
        public void errorItself(String errorM){
            errorMsg = errorM;
            error = true;
        }
    }
    static class headASTnode extends ASTnode{ // headASTnode, as beginning of list
        headASTnode(){
            super();
            id = "HEAD";
        }
        @Override
        public ASTnode eval(envASTnode env){
            if(sub == null){
                // nil is a kind of a list with no elemnts
                ASTnode list = new nilASTnode();
                list.next = next;
                list.asdata = asdata;
                list.quote = quote>=1?quote-1:0;
                return list;
            }
            if(asdata){ // Do not EVAL itself.
                return this;
            }
            if(quote > 0){ // DO not EVAl itself. Quoted
                quote --;
                return this;
            } else if(sub.error){
                errorItself(sub.errorMsg);
                return this;
            } else {
                ASTnode list =  sub.eval(env); // Now we can eval it.
                if(!list.asdata&&!"QUOTE".equals(sub.id)){
                // We eval "lambda" node in head node.
                // It is not easy to access actual parameters, if we evaluate
                // "lambda" in "LAMBDA" node itself
                if("LAMBDA".equals(list.id)){
                    if(list.next == null || list.next.next == null
                            ||!"HEAD".equals(list.next.id)
                            ||!"HEAD".equals(list.next.next.id)){
                        errorMsg = "Unknown lambda definition"
                                + list.next.toString();
                        error = true;
                        return this;
                    }
                    envASTnode envm = null;
                    ASTnode formerparam = list.next.sub.next;
                    ASTnode actualparam= next.next;
                    if(env == null){ 
                        envm = new envASTnode(list.next.sub, 
                                // Here is where treataadata really helps
                                next.eval(env).treatasdata());
                    } else {
                        envm = env.update(list.next.sub,
                                next.eval(env).treatasdata());
                    }
                    // Important. Actual parameters are sometimes more than one.
                    while(actualparam!=null&&formerparam!=null){
                        envm = envm.update(formerparam,
                              actualparam.cloneDeep().eval(env).treatasdata());
                        actualparam = actualparam.next;
                        formerparam = formerparam.next;
                    }
                    if(actualparam!=null||formerparam!=null){
                        errorItself("actual parameters are incompatible " +
                                "with formal parameters");
                        return this;
                    }
                    return list.next.next.sub.eval(envm);
                } else return list;
                } else {
                    return list;
                }
            }

        }
        // override, specify the return type as headASTnode
        public ASTnode clone() throws CloneNotSupportedException {
            return (headASTnode)(super.clone());
        }
        public void print(){ // Do Not Print its id.
            if(error){
                System.out.println("Wrong input");
                System.out.print(errorMsg);
            } else {
                for(int i= 0; i< quote; i++){
                    System.out.print("'");
                }
                if(sub != null){
                    System.out.print('['); // Distinguish from input
                    sub.print();
                    System.out.print(']');
                }
                if(next != null){
                    next.print();
                }
            }
        }
    }
    // environment.
    // envASTnode never appears in systax tree. Thus it has on overrided eval()
    // Instead, it has update and lookup.
    static class envASTnode extends ASTnode{
        ASTnode idname;
        ASTnode idval;
        envASTnode(ASTnode name, ASTnode val){
            super();
            id = "ENV";
            idname = name;
            idval = val;
        }
        public envASTnode update(ASTnode idname, ASTnode idval){
            envASTnode newid = new envASTnode(idname, idval);
            newid.next = this;
            return newid;
        }
        public ASTnode lookup(ASTnode id){
            if(this.idname.id.equals(id.id)){
                return this.idval.cloneDeep();
            } else {
                if(this.next!=null){
                    return ((envASTnode)(this.next)).lookup(id);
                } else {
                    return null;
                }
            }
        }

    }
    static class consASTnode extends ASTnode{
        consASTnode(){
            super();
            id = "CONS";
        }
        public ASTnode eval(envASTnode env){
            // Situations we cannot eval it.
            if(asdata){
                return this;
            }
            if(quote-- > 0){
                return this;
            }
            if(next == null || next.next == null){
                errorItself("Too few arguments given to CONS");
                return this;
            }
            ASTnode element = next.cloneDeep().eval(env);
            ASTnode list = next.next.cloneDeep().eval(env);
            // We are not allowed to cons one element to another one.
            // Different from clisp.
            if((!"HEAD".equals(list.id) && (!"NIL".equals(list.id)))
                    ||element.error||list.error){
                if(element.error||list.error){
                    errorItself(element.errorMsg + list.errorMsg);
                } else {
                    errorItself("Second param of cons is not list");
                }
                return this;
            }
            ASTnode newlist = new headASTnode();
            if("NIL".equals(list.id)){
                newlist.sub = element;
            } else {
                newlist = list;
                list = list.sub;
                element.next = list;
                newlist.sub = element;
            }
            newlist.next = null;
            return newlist;
        }
        public ASTnode clone() throws CloneNotSupportedException {
            return (consASTnode)(super.clone());
        }
    }
    static class carASTnode extends ASTnode{
        carASTnode(){
            super();
            id = "CAR";
        }
        public ASTnode eval(envASTnode env){
            // Situations we cannot eval it.
            if(asdata){
                return this;
            }
            if(quote-- > 0){
                return this;
            }
            if(next == null){
                errorItself("Too few arguments given to CAR");
                return this;
            }
            ASTnode list = next.eval(env);
            // Important detail. (car ''(1 3)) is QUOTE.
            // It is important because we cannot build self interpreter, if we
            // miss this detail. I find it out in last minutes.
            if(list.quote>0){
                return new quoteASTnode();
            }
            //Like cons, we are unable to (car 1 . 1)
            if(!"HEAD".equals(list.id)&&!"NIL".equals(list.id)||list.error){
                if(list.error){
                    errorItself(list.errorMsg);
                } else {
                    errorItself( "CAR: argument is not a list\n"+list.toString());
                }
                return this;
            }
            if("HEAD".equals(list.id)){
                list.sub.asdata = list.asdata;
                list = list.sub;
                list.next = null;
            }
            return list;
        }
        public ASTnode clone() throws CloneNotSupportedException {
            return (carASTnode)(super.clone());
        }
    }
    static class cdrASTnode extends ASTnode{
        cdrASTnode(){
            super();
            id = "CDR";
        }
        public ASTnode eval(envASTnode env){
            if(asdata){
                return this;
            }
            if(quote-- > 0){
                return this;
            }
            if(next == null){
                errorItself("Too few arguments given to CDR");
                return this;
            }
            ASTnode list = next.eval(env);
            // ALSO IMPORTANT. (cdr ''(1 3)) is ((1 3))
            if(list.quote>0){
                ASTnode head = new headASTnode();
                head.sub = list;
                list.quote -- ;
                return head;
            }
            //Like cons, we are unable to (cdr 1 . 1)
            if(!"NIL".equals(list.id)&&!"HEAD".equals(list.id)||list.error){
                if(list.error){
                    errorItself(list.errorMsg);
                } else {
                    errorItself( "CDR: argument is not a list\n"+list.toString());
                }
                return this;
            }
            if("HEAD".equals(list.id)){
                if(list.sub.next == null){
                    return new nilASTnode();
                }
                list.sub = list.sub.next;
                list.next = null;
            }
            return list;
        }
        public ASTnode clone() throws CloneNotSupportedException {
            return (cdrASTnode)(super.clone());
        }
    }
    static class nullASTnode extends ASTnode{
        nullASTnode(){
            super();
            id = "NULL";
        }
        public ASTnode eval(envASTnode env){
            if(asdata){
                return this;
            }
            if(quote>0){
                quote --;
                return this;
            }
            if(next == null){
                errorItself("Too few arguments given to NULL");
                return this;
            }
            ASTnode list = next.eval(env);
            if(list.error){
                errorItself(list.errorMsg);
                return this;
            }
            try {
                list = (nilASTnode)list; // cast next to nil
                return new tASTnode();
            } catch(Exception e){
                // I actually have fASTnode here.
                // "f" is not the same thing with "NIL"
                // Different
                return new fASTnode();
            }
        }
        public ASTnode clone() throws CloneNotSupportedException {
            return (nullASTnode)(super.clone());
        }
    }
    static class atomASTnode extends ASTnode{
        atomASTnode(){
            super();
            id = "ATOM";
        }
        public ASTnode eval(envASTnode env){
            if(asdata){
                return this;
            }
            if(quote-->0){
                return this;
            }
            if(next == null){
                errorItself("Too few arguments given to ATOM");
                return this;
            }
            ASTnode list = next.eval(env);
            if(list.error){
                errorItself( list.errorMsg);
                return this;
            }
            // atom means it is not a list.
            if(list.sub == null){
                return new tASTnode();
            } else {
                return new fASTnode();
            }
        }
    }
    static class numberpASTnode extends ASTnode{
        numberpASTnode(){
            super();
            id = "NUMBERP";
        }
        public ASTnode eval(envASTnode env){
            if(asdata){
                return this;
            }
            if(quote-- >0){
                return this;
            }
            if(next == null){
                errorItself("Too few arguments given to NUMBERP");
                return this;
            }
            ASTnode list = next.eval(env);
            if(list.error){
                errorItself(list.errorMsg);
                return this;
            }
            try{
                // cast it to numASTnode
                ASTnode num = (numASTnode)list;
                return new tASTnode();
            } catch(Exception e){
                return new fASTnode();
            }
        }
    }
    static class stringpASTnode extends ASTnode{
        stringpASTnode(){
            super();
            id = "STRINGP";
        }
        public ASTnode eval(envASTnode env){
            if(asdata){
                return this;
            }
            if(quote-->0){
                return this;
            }
            if(next == null){
                errorItself( "Too few arguments given to STRINGP");
                return this;
            }
            ASTnode list = next.eval(env);
            if(list.error){
                errorItself( list.errorMsg);
                return this;
            }
            try{
                //cast it to stringASTnode
                ASTnode s = (stringASTnode)list;
                return new tASTnode();
            } catch(Exception e){
                return new fASTnode();
            }
        }
    }
    static class errorASTnode extends ASTnode{
        errorASTnode(){
            super();
            id = "ERROR";
        }
        public ASTnode eval(envASTnode env){
            if(asdata){
                return this;
            }
            if(quote-->0){
                return this;
            }
            if(this.next == null){
                errorItself( "Too few arguments given to ERROR");
                return this;
            }
           
            ASTnode errorOutString = next;
            errorMsg = "ERROR: " + errorOutString.toString();
            if(next.next!= null){
                    errorMsg += next.eval(env).toString();
            }
            error = true;
            return this;
        }
    }
    static class quoteASTnode extends ASTnode{
        quoteASTnode(){
            super();
            id = "QUOTE";
        }
        public ASTnode eval(envASTnode env){
            if(asdata){
                return this;
            }
            if(quote>0){
                quote --;
                return this;
            }
            if(this.next == null){
                errorItself( "Too few arguments given to QUOTE");
                return this;
            }
            // I used to have next.quote++ here. Then I found it is not right.
            // Because we return next. not next.eval(true)
            return next;
        }
    }
    static class condASTnode extends ASTnode{
        condASTnode(){
            super();
            id = "COND";
        }
        public ASTnode eval(envASTnode env){
            if(asdata){
                return this;
            }
            if(quote-->0){
                return this;
            }
            if(next == null){
                errorItself("Too few arguments given to COND");
                return this;
            }
            ASTnode list = next;
            ASTnode condition = null;
            while("HEAD".equals(list.id)){
                if(list.sub == null){
                    break;
                }
                condition = list.sub.cloneDeep().eval(env);
                // try to find a T statement
                if("T".equals(condition.id)&&!condition.error){
                    return list.sub.next.cloneDeep().eval(env);
                } else if("F".equals(condition.id)&&!condition.error){
                    // go to next line
                    if(list.next!=null){
                        list = list.next;
                    } else {
                        break;
                    }
                } else {
                    break;
                }
            }
            if(condition.error){
                errorItself( condition.errorMsg);
            } else {
                errorItself("COND: cannot find T argument\n"
                        + condition.toString());
            }
            return this;
        }
        public ASTnode clone() throws CloneNotSupportedException {
            return (condASTnode)(super.clone());
        }
    }
    static class stringASTnode extends ASTnode{
        stringASTnode(String s){
            super();
            id = s;
        }
        public ASTnode eval(envASTnode env){
            if(asdata){
                return this;
            }
            if(quote>0){
                quote --;
                return this;
            }
            next = null;
            return this;
        }
    }
    static class eqASTnode extends ASTnode{
        eqASTnode(){
            super();
            id = "EQ";
        }
        public ASTnode eval(envASTnode env){
            if(asdata){
                return this;
            }
            if(quote-->0){
                return this;
            }
            if(next == null||next.next == null){
                errorItself("Too few arguments given to EQ");
                return this;
            }
            ASTnode left = next.cloneDeep().eval(env);
            ASTnode right = next.next.cloneDeep().eval(env);
            if(left.error||right.error){
                errorItself(left.errorMsg + right.errorMsg);
                return this;
            }
            ASTnode list = null;
            // I will keep the following two lines, which play a big role in
            // debuging.
//            System.out.println("left is ["+left.id+"]");
//            System.out.println("right is ["+right.id+"]");
            // We only compare id. For numbers, we also do so.
            if(left.id.equals(right.id)){
                list = new tASTnode();
            } else {
                list = new fASTnode();
            }
            return list;
        }
        public ASTnode clone() throws CloneNotSupportedException {
            return (eqASTnode)(super.clone());
        }
    }

    static class nilASTnode extends ASTnode{
        nilASTnode(){
            super();
            id = "NIL";
        }
        public ASTnode eval(envASTnode env){
            if(asdata){
                return this;
            }
            if(quote-->0){
                return this;
            }
            return this;
        }
        public void print(){
            System.out.print("NIL");
        }
        public ASTnode clone() throws CloneNotSupportedException {
            return (nilASTnode)(super.clone());
        }
    }
    static class tASTnode extends ASTnode{
        tASTnode(){
            super();
            id = "T";
        }
        public ASTnode eval(envASTnode env){
            if(asdata){
                return this;
            }
            if(quote-->0){
                return this;
            }
            return this;
        }
        public ASTnode clone() throws CloneNotSupportedException {
            return (tASTnode)(super.clone());
        }
    }
    static class fASTnode extends ASTnode{
        fASTnode(){
            super();
            id = "F";
        }
        public ASTnode eval(envASTnode env){
            if(asdata){
                return this;
            }
            if(quote-->0){
                return this;
            }
            return this;
        }
        public ASTnode clone() throws CloneNotSupportedException {
            return (fASTnode)(super.clone());
        }
    }
    static class idASTnode extends ASTnode{
        idASTnode(String s){
            super();
            id = s;
        }
        public ASTnode eval(envASTnode env){
            if(asdata){
                return this;
            }
            if(quote>0){
                quote --;
                return this;
            } else {
              // lookup id in environment. If found, it should be a function
              if(environment!=null){
                    ASTnode list = environment.lookup(this);
                    if(list!=null){
                        list.next = next;
                        return list.eval(env);
                    }
              }
              if(env != null){
                  // lookup id in its own env
                    ASTnode idval =  env.lookup(this);
                    if(idval!=null){
                        idval.next = null;
                        return idval;
                    }
              }
              // Lazy way
              id += "-unfound";
              return this;
            }
        }
        public ASTnode clone() throws CloneNotSupportedException {
            return (idASTnode)(super.clone());
        }
    }
    static class numASTnode extends ASTnode{
        numASTnode(String s){
            super();
            id = s;
        }
        public ASTnode eval(envASTnode env){
            if(asdata){
                return this;
            }
            if(quote>0){
                quote --;
                return this;
            }
            next = null;
            return this;
        }
        public ASTnode clone() throws CloneNotSupportedException {
            return (numASTnode)(super.clone());
        }
    }
    static class lambdaASTnode extends ASTnode{
        lambdaASTnode(){
            super();
            id = "LAMBDA";
        }
        // No worries. We eval it in next steps in headASTnode
        public ASTnode eval(envASTnode env){
            if(asdata){
                return this;
            }
            if(quote-->0){
                return this;
            }
            return this;
        }
        public ASTnode clone() throws CloneNotSupportedException {
            return (lambdaASTnode)(super.clone());
        }
    }
    static class defunASTnode extends ASTnode{
        defunASTnode(){
            super();
            id = "DEFUN";
        }
        public ASTnode eval(envASTnode env){
            if(asdata){
                return this;
            }
            if(quote-->0){
                return this;
            }
            if(next== null || next.next == null|| next.next.next == null){
                errorItself("Too few arguments given to DEFUN");
                return this;
            }
            ASTnode id = next.cloneDeep();
            lambdaASTnode thisStmt = new lambdaASTnode();
            thisStmt.next = next.next.cloneDeep();
            headASTnode thisFunc = new headASTnode();
            thisFunc.sub = thisStmt;
            id.next = null;
            // insert it into environment
            // We don't evaluate it now,
            // which means it is binding only when we use it
            if(environment == null){
                environment = new envASTnode(id, thisFunc);
            } else {
                environment = environment.update(id, thisFunc);
            }
            return id;
        }
        public ASTnode clone() throws CloneNotSupportedException {
            return (defunASTnode)(super.clone());
        }
    }

    ASTnode parser = null;
    static ASTnode generateAST(char token[], int start, int end){
        // generateAST not only generate AST, but also tokenize the codes
        ASTnode parsertree =  null;
        ASTnode current = null;
        int i = start,j;
        int quote = 0;
        while(i <= end){
            // a new list
            if(token[i] == '(') {
                j = i+1;
                int countp = 0;
                // found its ")", not the first ")" we met
                // I made the mistake, at first
                while(j<=end){
                    if(countp == 0 && token[j]==')'){
                        break;
                    } else if(token[j]==')'){
                        countp --;
                    } else if(token[j]=='('){
                        countp ++;
                    }
                    j++;
                }
                // tedious work
                if(parsertree == null){
                    parsertree = new headASTnode();
                    current = parsertree;
                } else {
                    current.next = new headASTnode();
                    current = current.next;
                }
                current.quote = quote;
                quote = 0;
                if(j==i+1){
                    current.sub = null;
                } else {
                    // call itself recursively
                    ASTnode subtree = generateAST(token, i+1, j-1 );
                    current.sub = subtree;
                }
                i = j+1;
            } else if(Character.isLetter(token[i])){
                j = i;
                //reach end of the word
                while(j <= end && token[j] != ' ' && token[j]!= '(' && token[j]!= ')'&&token[j]!=
                        '\n'&&token[j]!='\t'){
                    j++;
                }
                String s = new String(token, i, j-i);
                // keywords
                if("cons".equals(s)){
                    // tedious work
                    if(parsertree == null){
                        parsertree = new consASTnode();
                        current = parsertree;
                    } else {
                        current.next = new consASTnode();
                        current = current.next;
                    }
                } else if("car".equals(s)){
                    if(parsertree == null){
                        parsertree = new carASTnode();
                        current = parsertree;
                    } else {
                        current.next = new carASTnode();
                        current = current.next;
                    }
                } else if("cdr".equals(s)){
                    if(parsertree == null){
                        parsertree = new cdrASTnode();
                        current = parsertree;
                    } else {
                        current.next = new cdrASTnode();
                        current = current.next;
                    }
                } else if("cond".equals(s)){
                    if(parsertree == null){
                        parsertree = new condASTnode();
                        current = parsertree;
                    } else {
                        current.next = new condASTnode();
                        current = current.next;
                    }
                } else if("eq".equals(s)){
                    if(parsertree == null){
                        parsertree = new eqASTnode();
                        current = parsertree;
                    } else {
                        current.next = new eqASTnode();
                        current = current.next;
                    }
                } else if("null".equals(s)){
                    if(parsertree == null){
                        parsertree = new nullASTnode();
                        current = parsertree;
                    } else {
                        current.next = new nullASTnode();
                        current = current.next;
                    }
                } else if("lambda".equals(s)){
                    if(parsertree == null){
                        parsertree = new lambdaASTnode();
                        current = parsertree;
                    } else {
                        current.next = new lambdaASTnode();
                        current = current.next;
                    }
                } else if("defun".equals(s)){
                    if(parsertree == null){
                        parsertree = new defunASTnode();
                        current = parsertree;
                    } else {
                        current.next = new defunASTnode();
                        current = current.next;
                    }
                } else if("atom".equals(s)){
                    if(parsertree == null){
                        parsertree = new atomASTnode();
                        current = parsertree;
                    } else {
                        current.next = new atomASTnode();
                        current = current.next;
                    }
                } else if("numberp".equals(s)){
                    if(parsertree == null){
                        parsertree = new numberpASTnode();
                        current = parsertree;
                    } else {
                        current.next = new numberpASTnode();
                        current = current.next;
                    }
                } else if("stringp".equals(s)){
                    if(parsertree == null){
                        parsertree = new stringpASTnode();
                        current = parsertree;
                    } else {
                        current.next = new stringpASTnode();
                        current = current.next;
                    }
                } else if("error".equals(s)){
                    if(parsertree == null){
                        parsertree = new errorASTnode();
                        current = parsertree;
                    } else {
                        current.next = new errorASTnode();
                        current = current.next;
                    }
                } else if("nil".equals(s)){
                    if(parsertree == null){
                        parsertree = new nilASTnode();
                        current = parsertree;
                    } else {
                        current.next = new nilASTnode();
                        current = current.next;
                    }
                } else if("t".equals(s)){
                    if(parsertree == null){
                        parsertree = new tASTnode();
                        current = parsertree;
                    } else {
                        current.next = new tASTnode();
                        current = current.next;
                    }
                } else if("quote".equals(s)){
                    if(parsertree == null){
                        parsertree = new quoteASTnode();
                        current = parsertree;
                    } else {
                        current.next = new quoteASTnode();
                        current = current.next;
                    }
                } else {
                    if(parsertree == null){
                        parsertree = new idASTnode(s);
                        current = parsertree;
                    } else {
                        current.next = new idASTnode(s);
                        current = current.next;
                    }
                }
                i = j+1;
                // quote it
                current.quote = quote;
                quote = 0;
            } else if(Character.isDigit(token[i])){
                j = i;
                while(j <= end &&Character.isDigit(token[j])&& token[j] != ' '
                        && token[j]!= '(' && token[j]!= ')'&&token[j]!='\n'&&
                        token[j]!='\t'){
                    j++;
                }
                String s = new String(token, i, j-i);
                // num is treated as string, the only difference are their classes
                if(parsertree == null){
                    parsertree = new numASTnode(s);
                    current = parsertree;
                } else {
                    current.next = new numASTnode(s);
                    current = current.next;
                }
                // quote it
                current.quote = quote;
                i = j+1;
                quote = 0;
            } else if(token[i] == '\''){
                // quote
                quote++;
                i++;
            } else if(token[i] == '"'){
                // string
                j = i+1;
                while(j <= end && token[j]!='"'){
                    j++;
                }
                String s = new String(token, i, j-i+1);
                if(parsertree == null){
                    parsertree = new stringASTnode(s);
                    current = parsertree;
                } else {
                    current.next = new stringASTnode(s);
                    current = current.next;
                }
                i = j+1;
                quote = 0;
            } else {
                i++;
            }
        }
        return parsertree;
    }
    // test whether it is the end of input
    public static int testEnd(String inputString){
        char inputArray[] = inputString.toCharArray();
        int countp = 0; 
        int j = 0;
        while(j < inputString.length()){
             if(countp == 0 && inputArray[j]==')'){
                  return -1;
             } else if(inputArray[j] == '"'){
                 int k  = j+1;
                 while(k < inputArray.length && inputArray[k] != '"'){
                     k++;
                 };
                 if(k == inputArray.length){
                     return 1;
                 } else {
                     j = k;
                 }
             } else if(inputArray[j]==')'){
                  countp --;
             } else if(inputArray[j]=='('){
                  countp ++;
             }
             j++;
        }
        return countp;
    }
    public static void main(String[] args) {
        String inString = "";
        int len;
        InputStream in = null;
        BufferedReader reader = null;
        InputStreamReader stream = null;
        while(true){
            try{
                if(args.length > 0) {
                    // read from file
                    File f = new File(args[1]);
                    in = new FileInputStream(f);
                    byte b[] = new byte[1024];
                    len = in.read(b);
                    in.close();
                    inString = new String(b,0,len);
                } else {
                    // read from system.in
                    inString = "";
                    System.out.print(">>>");
                    stream = new InputStreamReader(System.in);
                    reader = new BufferedReader(stream);
                    do {
                    String aline = reader.readLine();
                    // remove comments
                    int commentBegin = aline.indexOf(';');
                    if(commentBegin == -1){
                        inString += aline;
                    } else {
                        inString += aline.substring(0, commentBegin);
                    }
                    } while (testEnd(inString)>0);
                    if(testEnd(inString) == -1){
                        System.out.println("Unmatched parentheses");
                        continue;
                    }
                }

            } catch(Exception e){ // simplify
                    System.out.println(e);
                    System.exit(0);
            }
            // exit means quit
            if("exit".equals(inString)){
                break;
            }
            char inCharArray[] = inString.toLowerCase().toCharArray();
            // generate AST tree
            ASTnode ASTtree = generateAST(inCharArray, 0, inCharArray.length - 1);
            // always test null
            if(ASTtree != null){
            ASTtree.print();
            System.out.println("\nANS:");
            // set env to null
            envASTnode env = null;
            // eval and print it
            ASTtree.eval(env).print();
            System.out.println("");
            }
            if(args.length > 0){
                break;
            }
        }
    }
}

